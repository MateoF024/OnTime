package com.mateof24.websocket;

import com.google.gson.JsonObject;
import com.mateof24.OnTimeConstants;
import com.mateof24.api.TimerInfo;
import com.mateof24.event.TimerEventBus;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TimerWebSocketServer {

    private static TimerWebSocketServer instance;
    private ServerSocket serverSocket;
    private final CopyOnWriteArrayList<PrintWriter> clients = new CopyOnWriteArrayList<>();
    private ExecutorService executor;
    // Single sender thread: broadcasts are handed off from the server thread
    // (a slow TCP client with a full buffer must never block the tick) while
    // still delivering events to every client in order.
    private ExecutorService sendExecutor;
    private boolean running = false;
    private int port;

    private TimerWebSocketServer() {}

    public static TimerWebSocketServer getInstance() {
        if (instance == null) instance = new TimerWebSocketServer();
        return instance;
    }

    public void start(int port) {
        if (running) return;
        this.port = port;
        executor = Executors.newCachedThreadPool();
        sendExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "OnTime-WebSocket-Send");
            t.setDaemon(true);
            return t;
        });
        executor.submit(this::acceptLoop);

        TimerEventBus.registerOnStart(info -> broadcast(buildPayload("START", info)));
        TimerEventBus.registerOnFinish(info -> broadcast(buildPayload("FINISH", info)));
        TimerEventBus.registerOnPause(info -> broadcast(buildPayload("PAUSE", info)));
        TimerEventBus.registerOnResume(info -> broadcast(buildPayload("RESUME", info)));
        TimerEventBus.registerOnTick(info -> broadcast(buildPayload("TICK", info)));

        running = true;
        OnTimeConstants.LOGGER.info("OnTime WebSocket server started on port {}", port);
    }

    public void stop() {
        if (!running) return;
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        clients.forEach(PrintWriter::close);
        clients.clear();
        if (executor != null) executor.shutdownNow();
        if (sendExecutor != null) sendExecutor.shutdownNow();
        OnTimeConstants.LOGGER.info("OnTime WebSocket server stopped");
    }

    private void acceptLoop() {
        try {
            serverSocket = new ServerSocket(port);
            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    PrintWriter writer = new PrintWriter(
                            new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true);
                    clients.add(writer);
                    executor.submit(() -> clientLoop(client, writer));
                } catch (IOException e) {
                    if (running) OnTimeConstants.LOGGER.warn("WebSocket accept error", e);
                }
            }
        } catch (IOException e) {
            if (running) OnTimeConstants.LOGGER.error("WebSocket server error", e);
        }
    }

    private void clientLoop(Socket socket, PrintWriter writer) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            while (running && !socket.isClosed()) {
                reader.readLine();
            }
        } catch (IOException ignored) {
        } finally {
            clients.remove(writer);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void broadcast(String message) {
        // Called on the server thread via TimerEventBus — only enqueue here.
        if (!running || sendExecutor == null) return;
        try {
            sendExecutor.submit(() -> clients.removeIf(w -> {
                w.println(message);
                return w.checkError();
            }));
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // stop() raced the event; nothing to deliver.
        }
    }

    private String buildPayload(String event, TimerInfo info) {
        JsonObject obj = new JsonObject();
        obj.addProperty("event", event);
        obj.addProperty("name", info.name());
        obj.addProperty("currentSeconds", info.getCurrentSeconds());
        obj.addProperty("targetSeconds", info.getTargetSeconds());
        obj.addProperty("formattedTime", info.getFormattedTime());
        obj.addProperty("percentage", info.getPercentage());
        obj.addProperty("countUp", info.countUp());
        obj.addProperty("running", info.running());
        return obj.toString();
    }

    public boolean isRunning() { return running; }
    public int getPort() { return port; }
}