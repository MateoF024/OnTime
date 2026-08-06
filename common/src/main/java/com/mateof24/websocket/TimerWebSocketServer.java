package com.mateof24.websocket;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mateof24.OnTimeConstants;
import com.mateof24.api.OnTimeAPI;
import com.mateof24.api.TimerInfo;
import com.mateof24.api.TimerRunInfo;
import com.mateof24.config.ModConfig;
import com.mateof24.event.TimerEventBus;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The event feed, speaking two protocols on one port.
 *
 * <p>Until 5.0.0 the name was a lie: it accepted a socket and wrote lines of
 * JSON, so {@code new WebSocket("ws://…")} from a browser could not connect and
 * neither could the {@code ws} libraries of Node or Python. It now reads the
 * first line and decides: an HTTP request asking to upgrade gets RFC 6455
 * frames, and anything else gets the line protocol it always had.</p>
 *
 * <p>One port for both because that is what the setting means. A client that
 * used to work still works, once it has said the token.</p>
 *
 * <p><b>This is a breaking change for existing TCP consumers</b>, which
 * connected and received without saying anything. That was the security hole:
 * no authentication of any kind, on every interface.</p>
 */
public class TimerWebSocketServer {

    private static TimerWebSocketServer instance;

    private ServerSocket serverSocket;
    private final CopyOnWriteArrayList<Client> clients = new CopyOnWriteArrayList<>();
    private ExecutorService executor;
    // Single sender thread: broadcasts are handed off from the server thread
    // (a slow client with a full buffer must never block the tick) while still
    // delivering events to every client in order.
    private ExecutorService sendExecutor;
    private volatile boolean running = false;
    // TimerEventBus has no unregister, so the listeners are wired exactly once
    // for the lifetime of the JVM. Without this flag a stop()+start() cycle
    // (possible through the API) would register a second set and every event
    // would be broadcast twice, then three times, and so on.
    private boolean listenersRegistered = false;
    private int port;

    private final WebSocketGate gate = new WebSocketGate();

    private TimerWebSocketServer() {}

    public static TimerWebSocketServer getInstance() {
        if (instance == null) instance = new TimerWebSocketServer();
        return instance;
    }

    /** One connected consumer, whichever of the two protocols it speaks. */
    private final class Client {
        final Socket socket;
        final OutputStream out;
        final boolean websocket;

        Client(Socket socket, OutputStream out, boolean websocket) {
            this.socket = socket;
            this.out = out;
            this.websocket = websocket;
        }

        void send(String message) throws IOException {
            if (websocket) {
                WebSocketProtocol.writeText(out, message);
            } else {
                out.write((message + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        }

        void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    public void start(int port) {
        if (running) return;
        this.port = port;
        gate.open();
        executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "OnTime-WebSocket");
            t.setDaemon(true);
            return t;
        });
        sendExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "OnTime-WebSocket-Send");
            t.setDaemon(true);
            return t;
        });

        running = true;
        executor.submit(this::acceptLoop);

        if (!listenersRegistered) {
            TimerEventBus.registerOnStart(info -> broadcast(buildPayload("START", info)));
            TimerEventBus.registerOnFinish(info -> broadcast(buildPayload("FINISH", info)));
            TimerEventBus.registerOnPause(info -> broadcast(buildPayload("PAUSE", info)));
            TimerEventBus.registerOnResume(info -> broadcast(buildPayload("RESUME", info)));
            TimerEventBus.registerOnTick(info -> broadcast(buildPayload("TICK", info)));
            listenersRegistered = true;
        }

        OnTimeConstants.LOGGER.info("OnTime WebSocket server started on {}:{}",
                ModConfig.getInstance().getWebSocketBindAddress(), port);
        // The token is the only way in, so it has to be somewhere the operator
        // can read it. The console is where the web panel puts its own.
        OnTimeConstants.LOGGER.info("OnTime WebSocket token: {}", gate.token());
    }

    public void stop() {
        if (!running) return;
        running = false;
        gate.close();
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        clients.forEach(Client::close);
        clients.clear();
        if (executor != null) executor.shutdownNow();
        if (sendExecutor != null) sendExecutor.shutdownNow();
        OnTimeConstants.LOGGER.info("OnTime WebSocket server stopped");
    }

    /** The token this run of the server accepts, for whoever has to hand it out. */
    public String getToken() {
        return gate.token();
    }

    private void acceptLoop() {
        try {
            String bind = ModConfig.getInstance().getWebSocketBindAddress();
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(bind, port));
            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    executor.submit(() -> handle(client));
                } catch (IOException e) {
                    if (running) OnTimeConstants.LOGGER.warn("WebSocket accept error", e);
                }
            }
        } catch (IOException e) {
            if (running) OnTimeConstants.LOGGER.error("WebSocket server error", e);
        }
    }

    /**
     * Works out which protocol this is, checks the token, and then talks.
     *
     * <p>The first line decides. An HTTP request line means a browser or an
     * HTTP client; anything else is the line protocol, where the first line
     * must be the token and nothing is sent until it is.</p>
     */
    private void handle(Socket socket) {
        String address = socket.getInetAddress() == null
                ? "?" : socket.getInetAddress().getHostAddress();
        Client client = null;
        try {
            // A connection that says nothing must not hold a thread for ever.
            socket.setSoTimeout(15_000);
            InputStream in = socket.getInputStream();
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());

            String first = WebSocketProtocol.readLine(in);
            if (first == null) return;

            client = first.startsWith("GET ") || first.startsWith("HEAD ")
                    ? openWebSocket(socket, in, out, first, address)
                    : openLine(socket, out, first, address);
            if (client == null) return;

            // Connected, authenticated, and told what already exists: a
            // consumer that starts halfway through a countdown used to hear
            // nothing until the next event and so never knew it was running.
            client.send(buildHello());
            clients.add(client);

            pump(client, in);
        } catch (IOException ignored) {
            // A consumer that walks away is not an error worth logging.
        } finally {
            if (client != null) {
                clients.remove(client);
                gate.release();
            }
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private Client openWebSocket(Socket socket, InputStream in, OutputStream out,
                                 String first, String address) throws IOException {
        WebSocketProtocol.Handshake shake = WebSocketProtocol.readHandshake(first, in);
        if (shake == null || !shake.isUpgrade()) {
            write(out, WebSocketProtocol.rejectResponse(400, "Expected a WebSocket upgrade"));
            return null;
        }

        String provided = WebSocketGate.tokenOf(shake);
        WebSocketGate.Verdict verdict = gate.admit(address, provided, System.currentTimeMillis());
        if (verdict != WebSocketGate.Verdict.ALLOWED) {
            // Refused before the upgrade, so a browser is told in a language it
            // can show rather than being dropped without explanation.
            write(out, WebSocketProtocol.rejectResponse(
                    verdict == WebSocketGate.Verdict.TOO_MANY ? 503 : 401, reason(verdict)));
            return null;
        }

        String offered = shake.header("Sec-WebSocket-Protocol");
        String echoed = null;
        if (offered != null) {
            for (String value : offered.split(",")) {
                if (value.trim().startsWith(WebSocketGate.PROTOCOL_PREFIX)) {
                    echoed = value.trim();
                    break;
                }
            }
        }
        write(out, WebSocketProtocol.upgradeResponse(shake.header("Sec-WebSocket-Key"), echoed));
        // Framed from here on, and a client may sit silent for hours.
        socket.setSoTimeout(0);
        return new Client(socket, out, true);
    }

    private Client openLine(Socket socket, OutputStream out, String first, String address)
            throws IOException {
        WebSocketGate.Verdict verdict = gate.admit(address, first.trim(), System.currentTimeMillis());
        if (verdict != WebSocketGate.Verdict.ALLOWED) {
            JsonObject error = new JsonObject();
            error.addProperty("event", "ERROR");
            error.addProperty("reason", reason(verdict));
            out.write((error + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            return null;
        }
        socket.setSoTimeout(0);
        return new Client(socket, out, false);
    }

    /** Reads until the other end goes away, answering the frames that need it. */
    private void pump(Client client, InputStream in) throws IOException {
        while (running && !client.socket.isClosed()) {
            if (!client.websocket) {
                if (in.read() == -1) return;
                continue;
            }
            WebSocketProtocol.Frame frame;
            try {
                frame = WebSocketProtocol.readFrame(in);
            } catch (WebSocketProtocol.TooLargeException e) {
                WebSocketProtocol.writeClose(client.out, WebSocketProtocol.CLOSE_TOO_BIG, "Frame too large");
                return;
            } catch (WebSocketProtocol.ProtocolException e) {
                WebSocketProtocol.writeClose(client.out,
                        WebSocketProtocol.CLOSE_PROTOCOL_ERROR, e.getMessage());
                return;
            }
            if (frame == null) return;

            switch (frame.opcode()) {
                case WebSocketProtocol.OP_CLOSE -> {
                    WebSocketProtocol.writeClose(client.out, WebSocketProtocol.CLOSE_NORMAL, null);
                    return;
                }
                // A ping must be answered with its own payload, or a browser
                // decides the connection is dead and drops it.
                case WebSocketProtocol.OP_PING ->
                        WebSocketProtocol.writeFrame(client.out,
                                WebSocketProtocol.OP_PONG, frame.payload());
                default -> {
                    // This feed is one-way; anything a client sends is ignored
                    // rather than parsed, which is one fewer thing to get wrong.
                }
            }
        }
    }

    private static void write(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String reason(WebSocketGate.Verdict verdict) {
        return switch (verdict) {
            case NO_TOKEN -> "A token is required";
            case BAD_TOKEN -> "Invalid token";
            case LOCKED_OUT -> "Too many failed attempts; try again later";
            case TOO_MANY -> "Too many connections";
            case ALLOWED -> "";
        };
    }

    private void broadcast(String message) {
        // Called on the server thread via TimerEventBus — only enqueue here.
        if (!running || sendExecutor == null) return;
        try {
            sendExecutor.submit(() -> clients.removeIf(client -> {
                try {
                    client.send(message);
                    return false;
                } catch (IOException e) {
                    client.close();
                    gate.release();
                    return true;
                }
            }));
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // stop() raced the event; nothing to deliver.
        }
    }

    /** Everything already in flight, so a consumer starts knowing where it is. */
    String buildHello() {
        JsonObject obj = new JsonObject();
        obj.addProperty("event", "HELLO");
        obj.addProperty("protocolVersion", 1);
        JsonArray runs = new JsonArray();
        for (TimerRunInfo run : OnTimeAPI.getInstance().getRuns()) {
            runs.add(runJson(run));
        }
        obj.add("runs", runs);
        return obj.toString();
    }

    private static JsonObject runJson(TimerRunInfo run) {
        JsonObject obj = new JsonObject();
        obj.addProperty("runId", run.runId().toString());
        obj.addProperty("name", run.timerName());
        obj.addProperty("currentSeconds", run.currentSeconds());
        obj.addProperty("targetSeconds", run.targetSeconds());
        obj.addProperty("formattedTime", run.formattedTime());
        obj.addProperty("percentage", run.percentage());
        obj.addProperty("countUp", run.countUp());
        obj.addProperty("running", run.running());
        obj.addProperty("scope", run.audience() == null ? "GLOBAL" : run.audience().scope().name());
        obj.addProperty("audienceSize", run.audience() == null || run.audience().isGlobal()
                ? -1 : run.audience().players().size());
        return obj;
    }

    /**
     * One event.
     *
     * <p>{@code runId}, {@code scope} and {@code audienceSize} are added to
     * what 4.0.0 sent rather than replacing anything, so a consumer written
     * against the old shape reads the same fields it always did.</p>
     */
    String buildPayload(String event, TimerInfo info) {
        JsonObject obj = new JsonObject();
        obj.addProperty("event", event);
        obj.addProperty("name", info.name());
        obj.addProperty("currentSeconds", info.getCurrentSeconds());
        obj.addProperty("targetSeconds", info.getTargetSeconds());
        obj.addProperty("formattedTime", info.getFormattedTime());
        obj.addProperty("percentage", info.getPercentage());
        obj.addProperty("countUp", info.countUp());
        obj.addProperty("running", info.running());

        // The execution this event belongs to, when it can be told: an event
        // carries a timer's name, and with several runs of one timer a name is
        // no longer enough to say which one moved.
        for (TimerRunInfo run : OnTimeAPI.getInstance().getRunsOf(info.name())) {
            obj.addProperty("runId", run.runId().toString());
            obj.addProperty("scope", run.audience() == null ? "GLOBAL" : run.audience().scope().name());
            obj.addProperty("audienceSize", run.audience() == null || run.audience().isGlobal()
                    ? -1 : run.audience().players().size());
            break;
        }
        return obj.toString();
    }

    public boolean isRunning() { return running; }

    public int getPort() { return port; }

    public int getClientCount() { return gate.clientCount(); }
}
