package com.mateof24.webpanel;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mateof24.OnTimeConstants;
import com.mateof24.admin.AdminOps;
import com.mateof24.config.ModConfig;
import com.mateof24.event.TimerEventBus;
import com.mateof24.compat.VanillaCompat;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The web panel: HTTP, routes and a live stream. Nothing else.
 *
 * <p>It used to be a thousand lines with the page inside it as a text block and
 * the business logic beside it. The page now lives in the jar as three files a
 * browser can cache, and every action goes through one door —
 * {@link AdminOps} — which is what makes the panel able to do what the commands
 * and the in-game screen can do without anybody keeping three lists in step.</p>
 *
 * <p>No CDN and no framework: the resources are inside the jar, so the panel
 * works on a server with no route to the internet.</p>
 */
public class TimerWebPanel {

    private static TimerWebPanel instance;

    private HttpServer httpServer;
    private ExecutorService executor;
    private volatile boolean running = false;
    private boolean listenersRegistered = false;
    private int port;
    private MinecraftServer mcServer;

    private final CopyOnWriteArrayList<PrintWriter> sseClients = new CopyOnWriteArrayList<>();

    /**
     * Access token, regenerated on every start().
     *
     * <p>Every request carries it, as {@code ?t=} for the page and the stream —
     * neither can set a header — or as {@code X-OnTime-Token} for everything
     * else. Without it the panel would hand timer command editing, which the
     * server runs at operator level, to whoever can reach the port.</p>
     */
    private volatile String accessToken = null;

    /** Built on the server thread, read by the handlers. */
    private volatile String publishedState = "{}";

    /**
     * Whether the snapshot above is older than the world it describes.
     *
     * <p>Set instead of rebuilding whenever nobody is listening. Building it
     * means walking every timer, every run, every player and <em>every
     * advancement on the server</em> and serialising the lot to a string;
     * doing that on a cadence, for no one, is the one thing on the tick path
     * that costs a measurable amount. A request finds it stale and pays for it
     * then, which is the only moment the answer is wanted.</p>
     */
    private volatile boolean stateStale = true;

    private final ConcurrentHashMap<String, long[]> rateWindows = new ConcurrentHashMap<>();

    private static final String TOKEN_HEADER = "X-OnTime-Token";
    private static final String TOKEN_QUERY = "t";
    private static final long RATE_WINDOW_MS = 10_000L;
    private static final int RATE_MAX_PER_WINDOW = 60;
    // Called from the server tick handler, so once per tick: twenty of them is
    // one second. It used to say "every 4 ticks", which it never was -- the
    // snapshot was being rebuilt and pushed four times a second rather than
    // the once this was meant to be.
    private static final int STATE_REFRESH_EVERY = 20;
    private int stateRefreshCounter = 0;

    private TimerWebPanel() {}

    public static TimerWebPanel getInstance() {
        if (instance == null) instance = new TimerWebPanel();
        return instance;
    }

    // ==================================================================
    // Lifecycle
    // ==================================================================

    public void start(int port, MinecraftServer server) {
        if (running) return;
        this.port = port;
        this.mcServer = server;
        this.accessToken = generateToken();
        this.rateWindows.clear();

        try {
            String bind = ModConfig.getInstance().getWebPanelBindAddress();
            httpServer = HttpServer.create(new InetSocketAddress(bind, port), 0);
            executor = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "ontime-webpanel");
                t.setDaemon(true);
                return t;
            });
            httpServer.setExecutor(executor);

            httpServer.createContext("/", this::serveStatic);
            httpServer.createContext("/api/state", this::serveState);
            httpServer.createContext("/api/action", this::serveAction);
            httpServer.createContext("/api/lang", this::serveLang);
            httpServer.createContext("/api/suggest", this::serveSuggest);
            httpServer.createContext("/events", this::serveEvents);
            httpServer.start();
            running = true;
            // Seeded here rather than waiting for the first tick: a panel
            // opened in the moment between the two would otherwise be handed
            // an empty board and believe it.
            republish(server);

            if (!listenersRegistered) {
                // Any of them means the board moved; the panel asks for the
                // whole state rather than trying to patch it from an event,
                // which is one fewer thing that can drift out of step.
                TimerEventBus.registerOnRunStart(info -> nudge("START"));
                TimerEventBus.registerOnRunFinish(info -> nudge("FINISH"));
                TimerEventBus.registerOnRunPause(info -> nudge("PAUSE"));
                TimerEventBus.registerOnRunResume(info -> nudge("RESUME"));
                listenersRegistered = true;
            }

            OnTimeConstants.LOGGER.info("OnTime web panel: {}", getAccessUrlWithToken());
        } catch (IOException e) {
            OnTimeConstants.LOGGER.error("Could not start the web panel on port {}", port, e);
        }
    }

    public void stop() {
        if (!running) return;
        running = false;
        // Invalidate the token first: any request already in flight is refused.
        accessToken = null;
        sseClients.forEach(writer -> {
            try {
                writer.close();
            } catch (Exception ignored) {
            }
        });
        sseClients.clear();
        if (httpServer != null) httpServer.stop(0);
        if (executor != null) executor.shutdownNow();
        rateWindows.clear();
        OnTimeConstants.LOGGER.info("OnTime web panel stopped");
    }

    public boolean isRunning() { return running; }

    public int getPort() { return port; }

    public int getConnectedClients() { return sseClients.size(); }

    public String getAccessUrl() { return "http://localhost:" + port + "/"; }

    /** The address to hand the operator, token included. */
    public String getAccessUrlWithToken() {
        String token = accessToken;
        return token == null ? getAccessUrl() : getAccessUrl() + "?" + TOKEN_QUERY + "=" + token;
    }

    /** Called from the tick handler; rebuilds the snapshot the panel reads. */
    public void onServerTick(MinecraftServer server) {
        if (!running) return;
        this.mcServer = server;
        // Nobody is listening, so nothing is built: the next request will find
        // the snapshot stale and build it then. The panel used to be ticked
        // unconditionally to stop it serving the past, which it still cannot
        // do -- but it can stop paying for a board no one has asked for.
        if (sseClients.isEmpty()) {
            stateStale = true;
            return;
        }
        if (++stateRefreshCounter < STATE_REFRESH_EVERY) return;
        stateRefreshCounter = 0;
        republish(server);
        // The clock moved. Cheap to say, and the panel decides what to do.
        broadcast("STATE", "{}");
    }

    private void nudge(String event) {
        if (!running) return;
        if (sseClients.isEmpty()) {
            stateStale = true;
            return;
        }
        republish(mcServer);
        broadcast(event, "{}");
    }

    /** Rebuilds the snapshot. Only ever called with somebody to send it to. */
    private void republish(MinecraftServer server) {
        publishedState = AdminOps.state(server).toString();
        stateStale = false;
    }

    // ==================================================================
    // Routes
    // ==================================================================

    /** The page and its two resources, straight out of the jar. */
    private void serveStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) {
            if (!authorized(ex)) return;
            byte[] page = resource("index.html");
            if (page == null) {
                send(ex, 500, "text/plain", "Panel resources missing from the jar".getBytes(StandardCharsets.UTF_8));
                return;
            }
            // The page carries the token so its own calls can present it
            // without it having to be parsed out of the address bar.
            String html = new String(page, StandardCharsets.UTF_8).replace(
                    "<script src=\"panel.js\"></script>",
                    "<script>window.ONTIME_TOKEN=\"" + accessToken + "\";</script>\n"
                            + "<script src=\"panel.js\"></script>");
            ex.getResponseHeaders().set("Cache-Control", "no-store, must-revalidate");
            send(ex, 200, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
            return;
        }

        // The two resources are public on purpose: they hold nothing, and
        // demanding a token for a stylesheet only breaks the browser's cache.
        String name = path.substring(1);
        if (!name.matches("[a-z0-9_.-]+")) {
            send(ex, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        byte[] body = resource(name);
        if (body == null) {
            send(ex, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        // These ship inside the jar and change with every update, and their
        // address never does. Without this the browser is entitled to keep the
        // copy it fetched the first time, so an updated mod serves a page whose
        // script and stylesheet are from whichever version was installed when
        // the tab was first opened.
        ex.getResponseHeaders().set("Cache-Control", "no-store, must-revalidate");
        send(ex, 200, contentType(name), body);
    }

    private void serveState(HttpExchange ex) throws IOException {
        if (!authorized(ex)) return;
        // Stale means the tick handler stopped building it because no stream
        // was open, so this is where it gets built. It also covers the case it
        // always covered: a request arriving before anything has published,
        // which would otherwise be answered with an empty board that looks
        // exactly like a real one.
        if (stateStale || "{}".equals(publishedState)) republish(mcServer);
        send(ex, 200, "application/json", publishedState.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The one door.
     *
     * <p>{@code {"op": "...", "args": {...}}} — the same names the in-game
     * screen sends and the same the commands land on. The panel has no
     * operations of its own, which is what "the same things" means in
     * practice.</p>
     */
    private void serveAction(HttpExchange ex) throws IOException {
        if (!authorized(ex)) return;
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, "application/json", "{\"error\":\"POST only\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (rateLimited(ex)) return;

        JsonObject request;
        try (InputStream in = ex.getRequestBody()) {
            request = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (Exception e) {
            send(ex, 400, "application/json", "{\"error\":\"Malformed request\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }

        String op = request.has("op") ? request.get("op").getAsString() : null;
        JsonObject args = request.has("args") && request.get("args").isJsonObject()
                ? request.getAsJsonObject("args") : new JsonObject();

        AdminOps.Result result = AdminOps.apply(mcServer, AdminOps.Caller.web("web panel"), op, args);
        if (result.stateChanged()) {
            // Through the same door as everything else, so the staleness flag
            // cannot end up saying the opposite of what the snapshot is.
            republish(mcServer);
            broadcast("STATE", "{}");
        }

        JsonObject answer = new JsonObject();
        answer.addProperty("success", result.success());
        if (result.message() != null && !result.message().isEmpty()) {
            answer.addProperty("message", result.message());
        }
        send(ex, 200, "application/json", answer.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Which language the panel should open in.
     *
     * <p>The server's own, which is the closest thing there is to "the game's":
     * a browser is not the game and knows nothing about it. Whoever is looking
     * can pick another, and the panel remembers that instead.</p>
     */
    private void serveLang(HttpExchange ex) throws IOException {
        if (!authorized(ex)) return;
        JsonObject answer = new JsonObject();
        answer.addProperty("language", Locale.getDefault().getLanguage());
        send(ex, 200, "application/json", answer.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * What the server would offer somebody typing this command.
     *
     * <p>The in-game field is the command block's own, and it is the game that
     * answers it. A browser has no dispatcher, so this is the same question
     * asked over the wire: brigadier parses the text against a level-4 source
     * and says what could come next, where the word being completed starts,
     * and how far the whole thing parsed before it gave up.</p>
     *
     * <p>Answered on the server thread. Completion reads the world — which
     * players are on, which objectives exist, which functions are loaded — and
     * the HTTP pool is not the thread allowed to look.</p>
     */
    private void serveSuggest(HttpExchange ex) throws IOException {
        if (!authorized(ex)) return;
        MinecraftServer server = mcServer;

        // One list, asked for once and kept: every sound event the game knows.
        // The board carries the advancements, the dimensions, the players and
        // the timers already, and the selectors are fixed by the game -- this
        // is the only one of the six the panel cannot work out for itself, and
        // it is far too long to repeat in a snapshot four times a second.
        if ("sounds".equals(queryParam(ex, "kind"))) {
            JsonObject list = new JsonObject();
            JsonArray ids = new JsonArray();
            list.add("list", ids);
            // Not named: this is ResourceLocation up to 1.21.11 and Identifier
            // from 26.1, and all that is wanted of it is how it prints.
            for (Object id : BuiltInRegistries.SOUND_EVENT.keySet()) {
                ids.add(String.valueOf(id));
            }
            ex.getResponseHeaders().set("Cache-Control", "private, max-age=3600");
            send(ex, 200, "application/json", list.toString().getBytes(StandardCharsets.UTF_8));
            return;
        }

        String query = queryParam(ex, "q");
        if (query == null) query = "";
        // Nothing sensible completes past this, and it caps what one request
        // can ask the dispatcher to chew on.
        if (query.length() > 256) query = query.substring(0, 256);

        JsonObject answer = new JsonObject();
        JsonArray offers = new JsonArray();
        JsonArray parsed = new JsonArray();
        answer.add("suggestions", offers);
        answer.add("parsed", parsed);

        if (server == null) {
            send(ex, 200, "application/json", answer.toString().getBytes(StandardCharsets.UTF_8));
            return;
        }

        final String text = query;
        try {
            server.submit(() -> {
                CommandSourceStack source = VanillaCompat.createCommandSource(
                        server, server.overworld(), "OnTime web panel");
                ParseResults<CommandSourceStack> results =
                        server.getCommands().getDispatcher().parse(text, source);
                // How far it got. Everything from here on is what the field
                // paints red, the same as the game does.
                answer.addProperty("cursor", results.getReader().getCursor());
                for (ParsedCommandNode<CommandSourceStack> node : results.getContext().getNodes()) {
                    JsonObject range = new JsonObject();
                    range.addProperty("start", node.getRange().getStart());
                    range.addProperty("end", node.getRange().getEnd());
                    parsed.add(range);
                }
                Suggestions suggestions = server.getCommands().getDispatcher()
                        .getCompletionSuggestions(results).join();
                answer.addProperty("start", suggestions.getRange().getStart());
                answer.addProperty("end", suggestions.getRange().getEnd());
                for (Suggestion suggestion : suggestions.getList()) {
                    JsonObject one = new JsonObject();
                    one.addProperty("text", suggestion.getText());
                    if (suggestion.getTooltip() != null) {
                        one.addProperty("tip", suggestion.getTooltip().getString());
                    }
                    offers.add(one);
                }
                return null;
            }).get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            // A suggestion nobody gets is a field that still works. Never a 500:
            // the panel would show an error toast on every keystroke.
            OnTimeConstants.LOGGER.debug("Could not complete '{}'", text, e);
        }
        send(ex, 200, "application/json", answer.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void serveEvents(HttpExchange ex) throws IOException {
        if (!authorized(ex)) return;
        ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.getResponseHeaders().set("Connection", "keep-alive");
        ex.sendResponseHeaders(200, 0);

        PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(ex.getResponseBody(), StandardCharsets.UTF_8), true);
        sseClients.add(writer);
        writer.write("event: STATE\ndata: {}\n\n");
        writer.flush();
        // The exchange stays open; the writer is closed by stop() or by the
        // broadcast that finds it broken.
    }

    private void broadcast(String event, String data) {
        if (sseClients.isEmpty()) return;
        String message = "event: " + event + "\ndata: " + data + "\n\n";
        sseClients.removeIf(writer -> {
            writer.write(message);
            writer.flush();
            return writer.checkError();
        });
    }

    // ==================================================================
    // Plumbing
    // ==================================================================

    private static byte[] resource(String name) {
        try (InputStream in = TimerWebPanel.class.getResourceAsStream("/webpanel/" + name)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static String contentType(String name) {
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        return "application/octet-stream";
    }

    private static void send(HttpExchange ex, int status, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        // The panel loads nothing from anywhere else, so it may as well say so.
        ex.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'self'; style-src 'self'; script-src 'self' 'unsafe-inline'; img-src 'self' data:");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }

    private static String generateToken() {
        byte[] bytes = new byte[16];
        new java.security.SecureRandom().nextBytes(bytes);
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return out.toString();
    }

    private static String queryParam(HttpExchange ex, String key) {
        String query = ex.getRequestURI().getRawQuery();
        if (query == null || query.isEmpty()) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            if (pair.substring(0, eq).equals(key)) {
                return java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /**
     * Refuses the exchange with 401 unless it carries the current token.
     * Compared in constant time, so a wrong one gives nothing away by timing.
     */
    private boolean authorized(HttpExchange ex) throws IOException {
        String expected = accessToken;
        String provided = ex.getRequestHeaders().getFirst(TOKEN_HEADER);
        if (provided == null) provided = queryParam(ex, TOKEN_QUERY);

        boolean ok = expected != null && provided != null
                && java.security.MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        provided.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            send(ex, 401, "application/json",
                    "{\"error\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8));
        }
        return ok;
    }

    /** A coarse per-address cap, so the one mutating route cannot be hammered. */
    private boolean rateLimited(HttpExchange ex) throws IOException {
        String address = ex.getRemoteAddress() == null ? "?"
                : ex.getRemoteAddress().getAddress().getHostAddress();
        long now = System.currentTimeMillis();
        long[] window = rateWindows.computeIfAbsent(address, key -> new long[]{now, 0});
        synchronized (window) {
            if (now - window[0] > RATE_WINDOW_MS) {
                window[0] = now;
                window[1] = 0;
            }
            if (++window[1] > RATE_MAX_PER_WINDOW) {
                send(ex, 429, "application/json",
                        "{\"error\":\"Too many requests\"}".getBytes(StandardCharsets.UTF_8));
                return true;
            }
        }
        return false;
    }
}
