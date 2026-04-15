package com.mateof24.webpanel;

import com.google.gson.*;
import com.mateof24.OnTimeConstants;
import com.mateof24.api.TimerInfo;
import com.mateof24.config.ModConfig;
import com.mateof24.event.TimerEventBus;
import com.mateof24.manager.TimerManager;
import com.mateof24.platform.Services;
import com.mateof24.tick.TimerTickHandler;
import com.mateof24.timer.Timer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class TimerWebPanel {

    private static TimerWebPanel instance;

    private HttpServer httpServer;
    private ExecutorService executor;
    private ScheduledExecutorService scheduler;
    private boolean running = false;
    private boolean listenersRegistered = false;
    private int port;
    private MinecraftServer mcServer;
    private final CopyOnWriteArrayList<PrintWriter> sseClients = new CopyOnWriteArrayList<>();
    private long lastClientActivityTime = 0L;
    private boolean shutdownWarned = false;

    private static final long INACTIVITY_MS = 5 * 60 * 1000L;
    private static final long WARN_AHEAD_MS = 60 * 1000L;

    private TimerWebPanel() {}

    public static TimerWebPanel getInstance() {
        if (instance == null) instance = new TimerWebPanel();
        return instance;
    }

    public void start(int port, MinecraftServer server) {
        if (running) return;
        this.port = port;
        this.mcServer = server;
        this.lastClientActivityTime = System.currentTimeMillis();
        this.shutdownWarned = false;

        try {
            httpServer = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
            executor = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "ontime-webpanel");
                t.setDaemon(true);
                return t;
            });
            httpServer.setExecutor(executor);
            httpServer.createContext("/", this::serveRoot);
            httpServer.createContext("/api/state", this::serveState);
            httpServer.createContext("/api/history", this::serveHistory);
            httpServer.createContext("/api/action", this::serveAction);
            httpServer.createContext("/api/timer", this::serveTimerCrud);
            httpServer.createContext("/api/config", this::serveConfig);
            httpServer.createContext("/events", this::serveSSE);
            httpServer.start();
            running = true;

            if (!listenersRegistered) {
                TimerEventBus.registerOnStart(info -> { if (running) { broadcastSSE(buildEvent("START", info)); broadcastState(); } });
                TimerEventBus.registerOnFinish(info -> { if (running) { broadcastSSE(buildEvent("FINISH", info)); broadcastState(); } });
                TimerEventBus.registerOnPause(info -> { if (running) { broadcastSSE(buildEvent("PAUSE", info)); broadcastState(); } });
                TimerEventBus.registerOnResume(info -> { if (running) { broadcastSSE(buildEvent("RESUME", info)); broadcastState(); } });
                listenersRegistered = true;
            }

            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ontime-webpanel-watchdog");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleAtFixedRate(this::checkInactivity, 30, 30, TimeUnit.SECONDS);

            OnTimeConstants.LOGGER.info("OnTime WebPanel started on port {}", port);
        } catch (IOException e) {
            running = false;
            OnTimeConstants.LOGGER.error("Failed to start OnTime WebPanel on port {}", port, e);
        }
    }

    public void stop() {
        if (!running) return;
        running = false;
        if (scheduler != null) { scheduler.shutdownNow(); scheduler = null; }
        sseClients.forEach(w -> { try { w.close(); } catch (Exception ignored) {} });
        sseClients.clear();
        if (httpServer != null) { httpServer.stop(0); httpServer = null; }
        if (executor != null) { executor.shutdownNow(); executor = null; }
        mcServer = null;
        OnTimeConstants.LOGGER.info("OnTime WebPanel stopped");
    }

    public boolean isRunning() { return running; }
    public int getPort() { return port; }
    public int getConnectedClients() { return sseClients.size(); }

    public String getAccessUrl() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return "http://" + addr.getHostAddress() + ":" + port + "/";
                    }
                }
            }
        } catch (Exception ignored) {}
        return "http://localhost:" + port + "/";
    }

    public void onServerTick(Timer activeTimer) {
        if (!running || sseClients.isEmpty()) return;
        JsonObject obj = new JsonObject();
        obj.addProperty("name", activeTimer.getName());
        obj.addProperty("currentSeconds", activeTimer.getCurrentTicks() / 20L);
        obj.addProperty("formattedTime", activeTimer.getFormattedTime());
        float pct = activeTimer.getTargetTicks() > 0
                ? (activeTimer.getCurrentTicks() * 100f) / activeTimer.getTargetTicks() : 100f;
        obj.addProperty("percentage", activeTimer.isCountUp() ? 100f - pct : pct);
        obj.addProperty("running", activeTimer.isRunning());
        broadcastSSE("event: TICK\ndata: " + obj + "\n\n");
    }

    private void broadcastState() {
        if (!running || sseClients.isEmpty()) return;
        broadcastSSE("event: STATE\ndata: " + buildStateJson() + "\n\n");
    }

    private void checkInactivity() {
        if (!running) return;
        if (!sseClients.isEmpty()) {
            lastClientActivityTime = System.currentTimeMillis();
            shutdownWarned = false;
            return;
        }
        long idle = System.currentTimeMillis() - lastClientActivityTime;
        if (!shutdownWarned && idle >= INACTIVITY_MS - WARN_AHEAD_MS) {
            shutdownWarned = true;
            notifyOps(Component.literal("\u00a7e[OnTime] \u00a7fWebPanel has no connections. It will close in 1 minute."));
        }
        if (idle >= INACTIVITY_MS) {
            notifyOps(Component.literal("\u00a7e[OnTime] \u00a7fWebPanel closed due to inactivity."));
            stop();
        }
    }

    private void notifyOps(Component message) {
        if (mcServer == null) return;
        mcServer.execute(() -> {
            mcServer.sendSystemMessage(message);
            for (ServerPlayer player : mcServer.getPlayerList().getPlayers()) {
                if (player.hasPermissions(4)) player.sendSystemMessage(message);
            }
        });
    }

    private void serveSSE(HttpExchange ex) throws IOException {
        lastClientActivityTime = System.currentTimeMillis();
        shutdownWarned = false;
        ex.getResponseHeaders().set("Content-Type", "text/event-stream");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.getResponseHeaders().set("Connection", "keep-alive");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(200, 0);

        PrintWriter writer = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(ex.getResponseBody(), StandardCharsets.UTF_8)), true);
        sseClients.add(writer);
        lastClientActivityTime = System.currentTimeMillis();
        sseWrite(writer, "INIT", buildStateJson().toString());

        try {
            while (running && !writer.checkError()) {
                try { Thread.sleep(10000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                if (!writer.checkError()) { writer.print(": ping\n\n"); writer.flush(); }
            }
        } finally {
            sseClients.remove(writer);
            lastClientActivityTime = System.currentTimeMillis();
            try { ex.getResponseBody().close(); } catch (IOException ignored) {}
        }
    }

    private void sseWrite(PrintWriter writer, String event, String data) {
        writer.print("event: " + event + "\n");
        writer.print("data: " + data + "\n\n");
        writer.flush();
    }

    private void broadcastSSE(String message) {
        sseClients.removeIf(w -> { w.print(message); w.flush(); return w.checkError(); });
    }

    private String buildEvent(String type, TimerInfo info) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", info.name());
        obj.addProperty("currentSeconds", info.getCurrentSeconds());
        obj.addProperty("targetSeconds", info.getTargetSeconds());
        obj.addProperty("formattedTime", info.getFormattedTime());
        obj.addProperty("percentage", info.getPercentage());
        obj.addProperty("countUp", info.countUp());
        obj.addProperty("running", info.running());
        obj.addProperty("repeat", info.repeat());
        obj.addProperty("repeatCount", info.repeatCount());
        obj.addProperty("repeatsDone", info.repeatsDone());
        return "event: " + type + "\ndata: " + obj + "\n\n";
    }

    private void serveRoot(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { ex.sendResponseHeaders(405, -1); return; }
        byte[] body = getDashboardHtml().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    private void serveState(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { ex.sendResponseHeaders(405, -1); return; }
        setCors(ex);
        byte[] body = buildStateJson().toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    private void serveHistory(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { ex.sendResponseHeaders(405, -1); return; }
        setCors(ex);
        String content = "[]";
        java.nio.file.Path histFile = Services.PLATFORM.getConfigDir().resolve("ontime").resolve("history.json");
        if (Files.exists(histFile)) {
            try { content = new String(Files.readAllBytes(histFile), StandardCharsets.UTF_8); }
            catch (IOException ignored) {}
        }
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    private void serveAction(HttpExchange ex) throws IOException {
        setCors(ex);
        if (ex.getRequestMethod().equalsIgnoreCase("OPTIONS")) { ex.sendResponseHeaders(204, -1); return; }
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) { ex.sendResponseHeaders(405, -1); return; }

        String reqBody;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8))) {
            reqBody = r.lines().collect(Collectors.joining());
        }

        JsonObject resp = new JsonObject();
        try {
            JsonObject req = new Gson().fromJson(reqBody, JsonObject.class);
            String action = req.get("action").getAsString();
            String timerName = req.has("timer") && !req.get("timer").isJsonNull()
                    ? req.get("timer").getAsString() : null;
            scheduleAction(action, timerName);
            resp.addProperty("success", true);
        } catch (Exception e) {
            resp.addProperty("success", false);
            resp.addProperty("error", e.getMessage());
        }

        byte[] body = resp.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    private void serveTimerCrud(HttpExchange ex) throws IOException {
        setCors(ex);
        if (ex.getRequestMethod().equalsIgnoreCase("OPTIONS")) { ex.sendResponseHeaders(204, -1); return; }
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) { ex.sendResponseHeaders(405, -1); return; }

        String reqBody;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8))) {
            reqBody = r.lines().collect(Collectors.joining());
        }

        JsonObject resp = new JsonObject();
        try {
            JsonObject req = new Gson().fromJson(reqBody, JsonObject.class);
            String action = req.get("action").getAsString();
            String name = req.has("name") ? req.get("name").getAsString().trim() : "";
            if (name.isEmpty()) throw new Exception("Name is required");
            if (mcServer == null) throw new Exception("Server not available");

            switch (action) {
                case "create" -> {
                    int hours = req.has("hours") ? req.get("hours").getAsInt() : 0;
                    int minutes = req.has("minutes") ? req.get("minutes").getAsInt() : 0;
                    int seconds = req.has("seconds") ? req.get("seconds").getAsInt() : 0;
                    boolean countUp = req.has("countUp") && req.get("countUp").getAsBoolean();
                    mcServer.execute(() -> {
                        if (TimerManager.getInstance().createTimer(name, hours, minutes, seconds, countUp)) {
                            TimerManager.getInstance().getTimer(name).ifPresent(t -> {
                                if (req.has("command")) t.setCommand(req.get("command").getAsString());
                                applyTimerProps(t, req);
                                TimerManager.getInstance().saveTimers();
                            });
                            broadcastState();
                        }
                    });
                }
                case "update" -> mcServer.execute(() ->
                        TimerManager.getInstance().getTimer(name).ifPresent(t -> {
                            if (req.has("command")) t.setCommand(req.get("command").getAsString());
                            applyTimerProps(t, req);
                            TimerManager.getInstance().saveTimers();
                            broadcastState();
                        })
                );
                case "delete" -> mcServer.execute(() -> {
                    if (TimerManager.getInstance().removeTimer(name)) {
                        Services.PLATFORM.sendTimerSyncPacket(mcServer, "", 0, 0, false, false, false);
                        broadcastState();
                    }
                });
                default -> throw new Exception("Unknown action: " + action);
            }
            resp.addProperty("success", true);
        } catch (Exception e) {
            resp.addProperty("success", false);
            resp.addProperty("error", e.getMessage());
        }

        byte[] body = resp.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    private void serveConfig(HttpExchange ex) throws IOException {
        setCors(ex);
        if (ex.getRequestMethod().equalsIgnoreCase("OPTIONS")) { ex.sendResponseHeaders(204, -1); return; }
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) { ex.sendResponseHeaders(405, -1); return; }

        String reqBody;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8))) {
            reqBody = r.lines().collect(Collectors.joining());
        }

        JsonObject resp = new JsonObject();
        try {
            JsonObject req = new Gson().fromJson(reqBody, JsonObject.class);
            if (mcServer == null) throw new Exception("Server not available");
            mcServer.execute(() -> {
                ModConfig cfg = ModConfig.getInstance();
                if (req.has("positionPreset"))
                    cfg.setPositionPreset(com.mateof24.config.TimerPositionPreset.fromString(req.get("positionPreset").getAsString()));
                if (req.has("scale")) cfg.setTimerScale(req.get("scale").getAsFloat());
                if (req.has("colorHigh")) cfg.setColorHigh(parseHexColor(req.get("colorHigh").getAsString()));
                if (req.has("colorMid")) cfg.setColorMid(parseHexColor(req.get("colorMid").getAsString()));
                if (req.has("colorLow")) cfg.setColorLow(parseHexColor(req.get("colorLow").getAsString()));
                if (req.has("thresholdMid")) cfg.setThresholdMid(req.get("thresholdMid").getAsInt());
                if (req.has("thresholdLow")) cfg.setThresholdLow(req.get("thresholdLow").getAsInt());
                if (req.has("soundId")) cfg.setTimerSoundId(req.get("soundId").getAsString());
                if (req.has("soundVolume")) cfg.setTimerSoundVolume(req.get("soundVolume").getAsFloat());
                if (req.has("soundPitch")) cfg.setTimerSoundPitch(req.get("soundPitch").getAsFloat());
                cfg.save();
                broadcastState();
            });
            resp.addProperty("success", true);
        } catch (Exception e) {
            resp.addProperty("success", false);
            resp.addProperty("error", e.getMessage());
        }

        byte[] body = resp.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    private void scheduleAction(String action, String timerName) {
        if (mcServer == null) return;
        mcServer.execute(() -> {
            switch (action) {
                case "start" -> {
                    if (timerName == null || !TimerManager.getInstance().hasTimer(timerName)) return;
                    if (TimerManager.getInstance().getActiveTimer().isPresent()) return;
                    if (TimerManager.getInstance().startTimer(timerName)) {
                        TimerManager.getInstance().getTimer(timerName).ifPresent(t ->
                                Services.PLATFORM.sendTimerSyncPacket(mcServer,
                                        t.getName(), t.getCurrentTicks(), t.getTargetTicks(),
                                        t.isCountUp(), t.isRunning(), t.isSilent()));
                    }
                }
                case "pause" -> TimerManager.getInstance().getActiveTimer().ifPresent(t -> {
                    t.setRunning(!t.isRunning());
                    TimerManager.getInstance().saveTimers();
                    Services.PLATFORM.sendTimerSyncPacket(mcServer,
                            t.getName(), t.getCurrentTicks(), t.getTargetTicks(),
                            t.isCountUp(), t.isRunning(), t.isSilent());
                    broadcastState();
                });
                case "stop" -> {
                    TimerTickHandler.cancelCooldown();
                    TimerManager.getInstance().getActiveTimer().ifPresent(t -> {
                        t.resetRepeatsDone();
                        t.reset();
                        TimerManager.getInstance().clearActiveTimer();
                    });
                    TimerManager.getInstance().saveTimers();
                    Services.PLATFORM.sendTimerSyncPacket(mcServer, "", 0, 0, false, false, false);
                    broadcastState();
                }
                case "reset" -> {
                    if (timerName == null) return;
                    TimerManager.getInstance().getTimer(timerName).ifPresent(t -> {
                        boolean wasActive = TimerManager.getInstance().getActiveTimer()
                                .map(a -> a.getName().equals(timerName)).orElse(false);
                        t.reset();
                        TimerManager.getInstance().saveTimers();
                        if (wasActive) Services.PLATFORM.sendTimerSyncPacket(mcServer,
                                t.getName(), t.getCurrentTicks(), t.getTargetTicks(),
                                t.isCountUp(), false, t.isSilent());
                        broadcastState();
                    });
                }
            }
        });
    }

    private void applyTimerProps(Timer t, JsonObject req) {
        if (req.has("silent")) t.setSilent(req.get("silent").getAsBoolean());
        if (req.has("repeat")) t.setRepeat(req.get("repeat").getAsBoolean());
        if (req.has("repeatCount")) t.setRepeatCount(req.get("repeatCount").getAsInt());
        if (req.has("repeatCooldown")) t.setRepeatCooldownTicks(req.get("repeatCooldown").getAsLong() * 20L);
        if (req.has("nextTimer")) {
            String next = req.get("nextTimer").getAsString();
            t.setNextTimer(next.isEmpty() ? null : next);
        }
        if (req.has("sequenceCooldown")) t.setSequenceCooldownTicks(req.get("sequenceCooldown").getAsLong() * 20L);
        if (req.has("conditionObjective")) {
            String obj = req.get("conditionObjective").getAsString();
            if (obj.isEmpty()) {
                t.clearCondition();
            } else {
                int score = req.has("conditionScore") ? req.get("conditionScore").getAsInt() : 0;
                String target = req.has("conditionTarget") ? req.get("conditionTarget").getAsString() : "*";
                t.setCondition(obj, score, target.isEmpty() ? "*" : target);
            }
        }
    }

    private int parseHexColor(String hex) {
        try { return Integer.parseInt(hex.replace("#", ""), 16); }
        catch (NumberFormatException e) { return 0xFFFFFF; }
    }

    private JsonObject buildStateJson() {
        JsonObject root = new JsonObject();
        JsonArray timers = new JsonArray();
        for (Timer t : TimerManager.getInstance().getAllTimers().values()) {
            timers.add(timerJson(t));
        }
        root.add("timers", timers);
        TimerManager.getInstance().getActiveTimer().ifPresent(t -> root.addProperty("active", t.getName()));

        ModConfig cfg = ModConfig.getInstance();
        JsonObject config = new JsonObject();
        config.addProperty("positionPreset", cfg.getPositionPreset().name());
        config.addProperty("scale", cfg.getTimerScale());
        config.addProperty("colorHigh", String.format("#%06X", cfg.getColorHigh()));
        config.addProperty("colorMid", String.format("#%06X", cfg.getColorMid()));
        config.addProperty("colorLow", String.format("#%06X", cfg.getColorLow()));
        config.addProperty("thresholdMid", cfg.getThresholdMid());
        config.addProperty("thresholdLow", cfg.getThresholdLow());
        config.addProperty("soundId", cfg.getTimerSoundId());
        config.addProperty("soundVolume", cfg.getTimerSoundVolume());
        config.addProperty("soundPitch", cfg.getTimerSoundPitch());
        root.add("config", config);
        return root;
    }

    private JsonObject timerJson(Timer t) {
        JsonObject json = new JsonObject();
        json.addProperty("name", t.getName());
        json.addProperty("currentSeconds", t.getCurrentTicks() / 20L);
        json.addProperty("targetSeconds", t.getTargetTicks() / 20L);
        json.addProperty("formattedTime", t.getFormattedTime());
        json.addProperty("running", t.isRunning());
        json.addProperty("countUp", t.isCountUp());
        json.addProperty("silent", t.isSilent());
        json.addProperty("command", t.getCommand() != null ? t.getCommand() : "");
        json.addProperty("repeat", t.isRepeat());
        json.addProperty("repeatCount", t.getRepeatCount());
        json.addProperty("repeatsDone", t.getRepeatsDone());
        json.addProperty("repeatCooldownSeconds", t.getRepeatCooldownTicks() / 20L);
        json.addProperty("nextTimer", t.getNextTimer() != null ? t.getNextTimer() : "");
        json.addProperty("sequenceCooldownSeconds", t.getSequenceCooldownTicks() / 20L);
        json.addProperty("hasCondition", t.hasCondition());
        json.addProperty("conditionObjective", t.getConditionObjective() != null ? t.getConditionObjective() : "");
        json.addProperty("conditionScore", t.getConditionScore());
        json.addProperty("conditionTarget", t.getConditionTarget() != null ? t.getConditionTarget() : "*");
        float pct = t.getTargetTicks() > 0 ? (t.getCurrentTicks() * 100f) / t.getTargetTicks() : 100f;
        json.addProperty("percentage", t.isCountUp() ? 100f - pct : pct);
        return json;
    }

    private void setCors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private String getDashboardHtml() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>OnTime Panel</title>
<style>
*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
:root{--bg:#0d1117;--s1:#161b22;--s2:#21262d;--bd:#30363d;--tx:#e6edf3;--mu:#7d8590;--ac:#3fb950;--ach:#2ea043;--wa:#d29922;--er:#f85149;--bl:#58a6ff;--r:10px;--rs:6px}
body{font-family:'Segoe UI',system-ui,sans-serif;background:var(--bg);color:var(--tx);min-height:100vh}
.hdr{background:var(--s1);border-bottom:1px solid var(--bd);padding:12px 20px;display:flex;align-items:center;gap:12px;position:sticky;top:0;z-index:100}
.logo{font-size:1rem;font-weight:700;display:flex;align-items:center;gap:8px}
.dot{width:8px;height:8px;border-radius:50%;background:var(--er);flex-shrink:0;transition:.3s}
.dot.ok{background:var(--ac)}
#cst{font-size:.78rem;color:var(--mu)}
.hurl{margin-left:auto;font-size:.75rem;color:var(--mu);font-family:monospace;background:var(--s2);padding:4px 10px;border-radius:var(--rs);border:1px solid var(--bd)}
.nav{display:flex;gap:2px;padding:8px 20px;background:var(--s1);border-bottom:1px solid var(--bd)}
.nb{padding:6px 14px;border:none;background:transparent;color:var(--mu);cursor:pointer;border-radius:var(--rs);font-size:.83rem;transition:.15s;border-bottom:2px solid transparent}
.nb:hover{color:var(--tx);background:var(--s2)}
.nb.on{color:var(--ac);border-bottom-color:var(--ac)}
.wrap{max-width:1080px;margin:0 auto;padding:20px}
.tab{display:none}.tab.on{display:block}
.card{background:var(--s1);border:1px solid var(--bd);border-radius:var(--r);padding:18px;margin-bottom:14px}
.ct{font-size:.68rem;font-weight:700;text-transform:uppercase;letter-spacing:.08em;color:var(--mu);margin-bottom:12px}
.empty{text-align:center;color:var(--mu);padding:28px 0;font-size:.88rem}
.at-name{text-align:center;font-size:.7rem;font-weight:700;text-transform:uppercase;letter-spacing:.15em;color:var(--mu);margin-bottom:4px}
.at-time{text-align:center;font-size:3.2rem;font-weight:900;font-variant-numeric:tabular-nums;letter-spacing:2px;font-family:'Courier New',monospace;transition:color .3s}
.at-bar{height:5px;background:var(--s2);border-radius:3px;margin:12px 0;overflow:hidden}
.at-fill{height:100%;border-radius:3px;transition:width .4s,background .4s}
.at-meta{display:flex;justify-content:center;gap:18px;margin-bottom:14px;font-size:.78rem;color:var(--mu)}
.at-meta span{color:var(--tx)}
.at-ctrl{display:flex;gap:8px;justify-content:center;flex-wrap:wrap}
.tr{display:flex;align-items:center;gap:10px;padding:11px 13px;background:var(--bg);border-radius:8px;margin-bottom:7px;border:1px solid var(--bd);transition:.15s}
.tr:last-child{margin-bottom:0}
.tr:hover{border-color:var(--s2)}
.tr-time{font-variant-numeric:tabular-nums;font-weight:700;min-width:76px;font-family:'Courier New',monospace;transition:color .3s}
.tr-info{flex:1;min-width:0}
.tr-name{font-weight:600;font-size:.88rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.tr-meta{font-size:.7rem;color:var(--mu);margin-top:2px}
.tr-act{display:flex;gap:5px;flex-shrink:0}
.bdg{display:inline-flex;align-items:center;padding:1px 7px;border-radius:4px;font-size:.65rem;font-weight:700;margin-left:5px;vertical-align:middle}
.b-run{background:rgba(63,185,80,.12);color:#3fb950;border:1px solid rgba(63,185,80,.3)}
.b-act{background:rgba(210,153,34,.12);color:#d29922;border:1px solid rgba(210,153,34,.3)}
.b-stp{background:rgba(125,133,144,.1);color:var(--mu);border:1px solid var(--bd)}
.add-row{display:flex;align-items:center;justify-content:center;gap:8px;padding:11px;border:1px dashed var(--bd);border-radius:8px;color:var(--mu);cursor:pointer;transition:.15s;font-size:.83rem;margin-top:4px}
.add-row:hover{border-color:var(--ac);color:var(--ac)}
.btn{display:inline-flex;align-items:center;gap:5px;padding:6px 13px;border:none;border-radius:var(--rs);cursor:pointer;font-size:.8rem;font-weight:600;transition:.15s;white-space:nowrap}
.btn-sm{padding:4px 9px;font-size:.73rem}
.btn-ic{padding:4px 7px}
.bp{background:var(--ac);color:#0d1117}.bp:hover{background:var(--ach)}
.bs{background:var(--s2);color:var(--tx);border:1px solid var(--bd)}.bs:hover{background:var(--bd)}
.bw{background:rgba(210,153,34,.12);color:var(--wa);border:1px solid rgba(210,153,34,.3)}.bw:hover{background:rgba(210,153,34,.22)}
.be{background:rgba(248,81,73,.12);color:var(--er);border:1px solid rgba(248,81,73,.3)}.be:hover{background:rgba(248,81,73,.22)}
.bg{background:transparent;color:var(--mu);border:1px solid var(--bd)}.bg:hover{color:var(--tx);border-color:var(--s2)}
.mo{position:fixed;inset:0;background:rgba(0,0,0,.75);display:none;align-items:center;justify-content:center;z-index:200;padding:16px}
.mo.op{display:flex}
.md{background:var(--s1);border:1px solid var(--bd);border-radius:var(--r);padding:22px;width:100%;max-width:540px;max-height:88vh;overflow-y:auto}
.md-hdr{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}
.md-ttl{font-size:.98rem;font-weight:700}
.md-x{background:none;border:none;color:var(--mu);cursor:pointer;font-size:1.1rem;padding:3px;line-height:1}.md-x:hover{color:var(--tx)}
.md-ft{display:flex;gap:8px;justify-content:flex-end;margin-top:18px;padding-top:14px;border-top:1px solid var(--bd)}
.fg{margin-bottom:14px}
.fr2{display:grid;grid-template-columns:1fr 1fr;gap:10px}
.fr3{display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px}
label{display:block;font-size:.76rem;font-weight:600;color:var(--mu);margin-bottom:5px}
input[type=text],input[type=number],select,textarea{width:100%;padding:7px 11px;background:var(--bg);border:1px solid var(--bd);border-radius:var(--rs);color:var(--tx);font-size:.88rem;transition:.15s;outline:none}
input:focus,select:focus{border-color:var(--ac)}
input[type=range]{width:100%;accent-color:var(--ac)}
input[type=color]{width:38px;height:30px;padding:2px;border:1px solid var(--bd);border-radius:var(--rs);background:var(--bg);cursor:pointer}
input[type=checkbox],input[type=radio]{accent-color:var(--ac);width:14px;height:14px}
.ck{display:flex;align-items:center;gap:7px;font-size:.85rem;cursor:pointer;user-select:none}
.sec{border:1px solid var(--bd);border-radius:var(--rs);margin-bottom:10px}
.sec-h{padding:9px 13px;cursor:pointer;display:flex;align-items:center;justify-content:space-between;font-size:.8rem;font-weight:600;user-select:none;transition:.15s}
.sec-h:hover{background:var(--s2);border-radius:var(--rs)}
.chev{transition:transform .2s;font-size:.75rem}
.chev.op{transform:rotate(180deg)}
.sec-b{padding:13px;border-top:1px solid var(--bd);display:none}
.sec-b.op{display:block}
.sg{display:grid;grid-template-columns:1fr 1fr;gap:14px}
.cr{display:flex;gap:10px;align-items:center;flex-wrap:wrap}
.ci{display:flex;align-items:center;gap:6px;font-size:.8rem}
.rv{font-size:.8rem;color:var(--tx);min-width:32px;text-align:right}
.toast-wrap{position:fixed;bottom:18px;right:18px;z-index:300;display:flex;flex-direction:column;gap:7px}
.toast{padding:10px 14px;border-radius:var(--rs);font-size:.82rem;animation:tsi .2s ease;max-width:280px}
.t-ok{background:rgba(63,185,80,.18);border:1px solid rgba(63,185,80,.4);color:#3fb950}
.t-er{background:rgba(248,81,73,.18);border:1px solid rgba(248,81,73,.4);color:#f85149}
@keyframes tsi{from{transform:translateX(100%);opacity:0}to{transform:translateX(0);opacity:1}}
@media(max-width:600px){.wrap{padding:14px}.sg{grid-template-columns:1fr}.at-time{font-size:2.4rem}}
</style>
</head>
<body>
<header class="hdr">
  <div class="logo"><span class="dot" id="dot"></span><span>⏱ OnTime</span></div>
  <span id="cst" class="mu">Connecting...</span>
  <div class="hurl" id="hurl"></div>
</header>
<nav class="nav">
  <button class="nb on" onclick="sw('dash',this)">Dashboard</button>
  <button class="nb" onclick="sw('settings',this)">Settings</button>
  <button class="nb" onclick="sw('history',this)">History</button>
</nav>
<main class="wrap">
  <div id="tab-dash" class="tab on">
    <div class="card" id="acard"><div class="empty">No active timer</div></div>
    <div class="card">
      <div class="ct">All Timers</div>
      <div id="tlist"></div>
      <div class="add-row" onclick="openCreate()"><span>＋</span><span>Create Timer</span></div>
    </div>
  </div>
  <div id="tab-settings" class="tab">
    <div class="card">
      <div class="ct">Display Settings</div>
      <div class="sg">
        <div class="fg"><label>Position Preset</label>
          <select id="c-preset">
            <option value="BOSSBAR">Boss Bar</option><option value="ACTIONBAR">Action Bar</option>
            <option value="TOP_LEFT">Top Left</option><option value="TOP_CENTER">Top Center</option>
            <option value="TOP_RIGHT">Top Right</option><option value="CENTER">Center</option>
            <option value="BOTTOM_LEFT">Bottom Left</option><option value="BOTTOM_CENTER">Bottom Center</option>
            <option value="BOTTOM_RIGHT">Bottom Right</option><option value="CUSTOM">Custom</option>
          </select>
        </div>
        <div class="fg"><label>Scale — <span id="c-sv">1.0</span></label>
          <input type="range" id="c-scale" min="0.1" max="5" step="0.1" value="1" oninput="gid('c-sv').textContent=parseFloat(this.value).toFixed(1)">
        </div>
      </div>
      <div class="fg"><label>Timer Colors</label>
        <div class="cr">
          <div class="ci"><input type="color" id="c-ch" value="#ffffff"> <span>High</span></div>
          <div class="ci"><input type="color" id="c-cm" value="#ffff00"> <span>Mid</span></div>
          <div class="ci"><input type="color" id="c-cl" value="#ff0000"> <span>Low</span></div>
        </div>
      </div>
      <div class="sg">
        <div class="fg"><label>Mid Threshold % — <span id="c-tmv">30</span></label>
          <input type="range" id="c-tm" min="0" max="100" value="30" oninput="gid('c-tmv').textContent=this.value">
        </div>
        <div class="fg"><label>Low Threshold % — <span id="c-tlv">10</span></label>
          <input type="range" id="c-tl" min="0" max="100" value="10" oninput="gid('c-tlv').textContent=this.value">
        </div>
      </div>
      <div class="fg"><label>Tick Sound ID</label><input type="text" id="c-sid" value="minecraft:block.note_block.hat"></div>
      <div class="sg">
        <div class="fg"><label>Volume — <span id="c-svv">1.00</span></label>
          <input type="range" id="c-sv2" min="0" max="1" step="0.05" value="1" oninput="gid('c-svv').textContent=parseFloat(this.value).toFixed(2)">
        </div>
        <div class="fg"><label>Pitch — <span id="c-spv">2.00</span></label>
          <input type="range" id="c-sp" min="0.5" max="2" step="0.05" value="2" oninput="gid('c-spv').textContent=parseFloat(this.value).toFixed(2)">
        </div>
      </div>
      <button class="btn bp" onclick="saveSettings()">Save Settings</button>
    </div>
  </div>
  <div id="tab-history" class="tab">
    <div class="card"><div class="ct">Completion History</div><div id="hlist"><div class="empty">Loading...</div></div></div>
  </div>
</main>

<div class="mo" id="tmo">
  <div class="md">
    <div class="md-hdr"><span class="md-ttl" id="mttl">Create Timer</span><button class="md-x" onclick="closeM()">✕</button></div>
    <div class="fg"><label>Name</label><input type="text" id="f-name" placeholder="e.g. speedrun"></div>
    <div id="f-dur" class="fg"><label>Duration</label>
      <div class="fr3">
        <div><label>Hours</label><input type="number" id="f-h" min="0" value="0"></div>
        <div><label>Minutes</label><input type="number" id="f-m" min="0" max="59" value="0"></div>
        <div><label>Seconds</label><input type="number" id="f-s" min="0" max="59" value="0"></div>
      </div>
    </div>
    <div id="f-mode" class="fg"><label>Mode</label>
      <div style="display:flex;gap:16px">
        <label class="ck"><input type="radio" name="cu" value="false" checked> ↓ Countdown</label>
        <label class="ck"><input type="radio" name="cu" value="true"> ↑ Count-up</label>
      </div>
    </div>
    <div class="sec">
      <div class="sec-h" onclick="tog(this)"><span>⚡ Command</span><span class="chev">▼</span></div>
      <div class="sec-b">
        <div class="fg"><label>On Finish (without /)</label><input type="text" id="f-cmd" placeholder="say Timer {name} finished!"></div>
        <label class="ck"><input type="checkbox" id="f-sil"> Silent timer (no tick sound)</label>
      </div>
    </div>
    <div class="sec">
      <div class="sec-h" onclick="tog(this)"><span>🔁 Repeat</span><span class="chev">▼</span></div>
      <div class="sec-b">
        <div class="fg"><label class="ck"><input type="checkbox" id="f-rep" onchange="updRep()"> Enable repeat</label></div>
        <div id="f-repf" style="display:none">
          <div class="fr2">
            <div class="fg"><label>Count (-1 = infinite)</label><input type="number" id="f-rc" value="-1" min="-1"></div>
            <div class="fg"><label>Cooldown (seconds)</label><input type="number" id="f-rcd" value="0" min="0"></div>
          </div>
        </div>
      </div>
    </div>
    <div class="sec">
      <div class="sec-h" onclick="tog(this)"><span>➡ Sequence</span><span class="chev">▼</span></div>
      <div class="sec-b">
        <div class="fr2">
          <div class="fg"><label>Next Timer</label><select id="f-nt"><option value="">— none —</option></select></div>
          <div class="fg"><label>Cooldown (seconds)</label><input type="number" id="f-scd" value="0" min="0"></div>
        </div>
      </div>
    </div>
    <div class="sec">
      <div class="sec-h" onclick="tog(this)"><span>📊 Scoreboard Condition</span><span class="chev">▼</span></div>
      <div class="sec-b">
        <div class="fr3">
          <div class="fg"><label>Objective</label><input type="text" id="f-co" placeholder="kills"></div>
          <div class="fg"><label>Score ≥</label><input type="number" id="f-cs" value="0" min="0"></div>
          <div class="fg"><label>Target (* = any)</label><input type="text" id="f-ct" value="*"></div>
        </div>
        <small style="color:var(--mu)">Leave Objective empty to clear condition.</small>
      </div>
    </div>
    <div class="md-ft">
      <button class="btn bg" onclick="closeM()">Cancel</button>
      <button class="btn bp" id="msave" onclick="saveTimer()">Create</button>
    </div>
  </div>
</div>

<div class="mo" id="dmo">
  <div class="md" style="max-width:360px;text-align:center">
    <div style="font-size:2rem;margin-bottom:10px">⚠️</div>
    <div style="font-size:1rem;font-weight:700;margin-bottom:8px">Delete Timer</div>
    <p id="dmsg" style="color:var(--mu);font-size:.88rem;margin-bottom:18px"></p>
    <div style="display:flex;gap:8px;justify-content:center">
      <button class="btn bg" onclick="closeDel()">Cancel</button>
      <button class="btn be" onclick="doDel()">Delete</button>
    </div>
  </div>
</div>

<div class="toast-wrap" id="tw"></div>

<script>
let S={timers:{},active:null,config:{}},es=null,editing=null,deleting=null;

function gid(i){return document.getElementById(i)}
function esc(s){return String(s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#39;')}
function gc(p){const{thresholdMid:m=30,thresholdLow:l=10,colorHigh:h='#3fb950',colorMid:cm='#d29922',colorLow:cl='#f85149'}=S.config;return p>m?h:p>l?cm:cl}

function connect(){
  if(es)es.close();
  es=new EventSource('/events');
  es.addEventListener('INIT',e=>{applyState(JSON.parse(e.data));setConn(true);});
  es.addEventListener('STATE',e=>{applyState(JSON.parse(e.data));});
  ['START','FINISH','PAUSE','RESUME'].forEach(ev=>{
    es.addEventListener(ev,e=>{
      const d=JSON.parse(e.data);
      if(ev==='START')S.active=d.name;
      if(ev==='FINISH'&&S.active===d.name)S.active=null;
      if(S.timers[d.name])Object.assign(S.timers[d.name],d);
      render();
    });
  });
  es.addEventListener('TICK',e=>{
    const d=JSON.parse(e.data);
    if(S.timers[d.name]){
      Object.assign(S.timers[d.name],d);
      if(S.active===d.name)updActive();
      updRow(d.name,d);
    }
  });
  es.onopen=()=>setConn(true);
  es.onerror=()=>{setConn(false);setTimeout(connect,3000);};
}

function applyState(d){
  S.timers={};
  for(const t of d.timers||[])S.timers[t.name]=t;
  S.active=d.active||null;
  if(d.config){S.config=d.config;loadCfg(d.config);}
  render();
}
function setConn(ok){gid('dot').className='dot'+(ok?' ok':'');gid('cst').textContent=ok?'Connected':'Reconnecting...';}

function sw(name,btn){
  document.querySelectorAll('.tab').forEach(t=>t.classList.remove('on'));
  document.querySelectorAll('.nb').forEach(b=>b.classList.remove('on'));
  gid('tab-'+name).classList.add('on');btn.classList.add('on');
  if(name==='history')loadHist();
}

function render(){renderActive();renderList();}

function renderActive(){
  const el=gid('acard'),t=S.active?S.timers[S.active]:null;
  if(!t){el.innerHTML='<div class="empty">No active timer</div>';return;}
  const p=Math.max(0,Math.min(100,t.percentage||0)),c=gc(p);
  const pause=t.running?'⏸ Pause':'▶ Resume';
  el.innerHTML=`<div class="at-name">${esc(t.name)}</div>
<div class="at-time" id="atd" style="color:${c}">${esc(t.formattedTime)}</div>
<div class="at-bar"><div class="at-fill" id="atb" style="width:${p}%;background:${c}"></div></div>
<div class="at-meta">
  <div>${t.countUp?'↑ count-up':'↓ countdown'}</div>
  <div>Progress: <span id="atp">${p.toFixed(1)}%</span></div>
  ${t.repeat?`<div>Repeat: <span>${t.repeatCount===-1?'∞':t.repeatsDone+'/'+t.repeatCount}</span></div>`:''}
</div>
<div class="at-ctrl">
  <button class="btn bw" onclick="act('pause')">${pause}</button>
  <button class="btn be" onclick="act('stop')">⏹ Stop</button>
  <button class="btn bs" onclick="act('reset','${esc(t.name)}')">↺ Reset</button>
  <button class="btn bg btn-sm" onclick="openEdit('${esc(t.name)}')">✎ Edit</button>
</div>`;
}

function updActive(){
  const t=S.active?S.timers[S.active]:null;if(!t)return;
  const p=Math.max(0,Math.min(100,t.percentage||0)),c=gc(p);
  const d=gid('atd'),b=gid('atb'),pt=gid('atp');
  if(!d){renderActive();return;}
  d.textContent=t.formattedTime;d.style.color=c;
  b.style.width=p+'%';b.style.background=c;
  pt.textContent=p.toFixed(1)+'%';
}

function updRow(name,data){
  const el=document.querySelector(`[data-ttime="${CSS.escape(name)}"]`);
  if(el){const p=Math.max(0,Math.min(100,data.percentage||0));el.textContent=data.formattedTime;el.style.color=gc(p);}
}

function renderList(){
  const el=gid('tlist'),ts=Object.values(S.timers);
  if(!ts.length){el.innerHTML='<div class="empty" style="padding:16px">No timers created yet</div>';return;}
  el.innerHTML=ts.map(t=>{
    const isA=t.name===S.active,p=Math.max(0,Math.min(100,t.percentage||0)),c=gc(p);
    const bdg=isA?'<span class="bdg b-act">ACTIVE</span>':t.running?'<span class="bdg b-run">RUNNING</span>':'<span class="bdg b-stp">STOPPED</span>';
    const meta=[];
    if(t.repeat)meta.push(t.repeatCount===-1?'∞ repeat':'×'+t.repeatCount);
    if(t.nextTimer)meta.push('→ '+t.nextTimer);
    if(t.hasCondition)meta.push('⚡ '+t.conditionObjective+'≥'+t.conditionScore);
    const canStart=!isA&&!t.running&&!S.active;
    return `<div class="tr">
  <div class="tr-time" data-ttime="${esc(t.name)}" style="color:${c}">${esc(t.formattedTime)}</div>
  <div class="tr-info"><div class="tr-name">${esc(t.name)}${bdg}</div>${meta.length?`<div class="tr-meta">${meta.map(esc).join(' · ')}</div>`:''}</div>
  <div class="tr-act">
    ${canStart?`<button class="btn bp btn-sm" onclick="act('start','${esc(t.name)}')">▶</button>`:''}
    <button class="btn bg btn-sm btn-ic" onclick="openEdit('${esc(t.name)}')" title="Edit">✎</button>
    <button class="btn be btn-sm btn-ic" onclick="openDel('${esc(t.name)}')" title="Delete">✕</button>
  </div>
</div>`;
  }).join('');
}

async function act(action,timer){
  await fetch('/api/action',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action,timer:timer||null})});
}

function openCreate(){
  editing=null;
  gid('mttl').textContent='Create Timer';gid('msave').textContent='Create';
  gid('f-name').value='';gid('f-name').disabled=false;
  gid('f-h').value=0;gid('f-m').value=0;gid('f-s').value=0;
  document.querySelectorAll('[name=cu]')[0].checked=true;
  gid('f-cmd').value='';gid('f-sil').checked=false;
  gid('f-rep').checked=false;gid('f-rc').value=-1;gid('f-rcd').value=0;
  gid('f-nt').value='';gid('f-scd').value=0;
  gid('f-co').value='';gid('f-cs').value=0;gid('f-ct').value='*';
  gid('f-dur').style.display='';gid('f-mode').style.display='';
  updRep();popNT(null);
  gid('tmo').classList.add('op');
}

function openEdit(name){
  const t=S.timers[name];if(!t)return;
  editing=name;
  gid('mttl').textContent='Edit Timer';gid('msave').textContent='Save';
  gid('f-name').value=name;gid('f-name').disabled=true;
  gid('f-dur').style.display='none';gid('f-mode').style.display='none';
  gid('f-cmd').value=t.command||'';gid('f-sil').checked=t.silent||false;
  gid('f-rep').checked=t.repeat||false;
  gid('f-rc').value=t.repeatCount!=null?t.repeatCount:-1;
  gid('f-rcd').value=t.repeatCooldownSeconds||0;
  popNT(name);
  gid('f-nt').value=t.nextTimer||'';gid('f-scd').value=t.sequenceCooldownSeconds||0;
  gid('f-co').value=t.conditionObjective||'';gid('f-cs').value=t.conditionScore||0;
  gid('f-ct').value=t.conditionTarget||'*';
  updRep();gid('tmo').classList.add('op');
}

function closeM(){gid('tmo').classList.remove('op');editing=null;}
function updRep(){gid('f-repf').style.display=gid('f-rep').checked?'block':'none';}

function popNT(ex){
  const sel=gid('f-nt'),cur=sel.value;
  sel.innerHTML='<option value="">— none —</option>';
  Object.keys(S.timers).filter(n=>n!==ex).forEach(n=>{
    const o=document.createElement('option');o.value=n;o.textContent=n;sel.appendChild(o);
  });
  sel.value=cur;
}

async function saveTimer(){
  const name=gid('f-name').value.trim();
  if(!name){toast('Name is required','er');return;}
  const p={action:editing?'update':'create',name};
  if(!editing){
    const h=parseInt(gid('f-h').value)||0,m=parseInt(gid('f-m').value)||0,s=parseInt(gid('f-s').value)||0;
    if(h+m+s===0){toast('Duration must be greater than 0','er');return;}
    p.hours=h;p.minutes=m;p.seconds=s;
    p.countUp=document.querySelector('[name=cu]:checked').value==='true';
  }
  p.command=gid('f-cmd').value.trim();p.silent=gid('f-sil').checked;
  p.repeat=gid('f-rep').checked;p.repeatCount=parseInt(gid('f-rc').value)||-1;
  p.repeatCooldown=parseInt(gid('f-rcd').value)||0;
  p.nextTimer=gid('f-nt').value;p.sequenceCooldown=parseInt(gid('f-scd').value)||0;
  p.conditionObjective=gid('f-co').value.trim();p.conditionScore=parseInt(gid('f-cs').value)||0;
  p.conditionTarget=gid('f-ct').value.trim()||'*';
  const r=await fetch('/api/timer',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(p)});
  const d=await r.json();
  if(d.success){toast(editing?'Timer updated':'Timer created','ok');closeM();}
  else toast(d.error||'Operation failed','er');
}

function openDel(name){deleting=name;gid('dmsg').textContent=`Delete "${name}"? This cannot be undone.`;gid('dmo').classList.add('op');}
function closeDel(){gid('dmo').classList.remove('op');deleting=null;}
async function doDel(){
  if(!deleting)return;const name=deleting;closeDel();
  const r=await fetch('/api/timer',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'delete',name})});
  const d=await r.json();
  if(d.success)toast('Timer deleted','ok');else toast(d.error||'Failed','er');
}

function loadCfg(c){
  if(!c)return;
  const v=(id,val)=>{const e=gid(id);if(e&&val!=null)e.value=val;};
  v('c-preset',c.positionPreset);
  if(c.scale!=null){gid('c-scale').value=c.scale;gid('c-sv').textContent=parseFloat(c.scale).toFixed(1);}
  v('c-ch',c.colorHigh);v('c-cm',c.colorMid);v('c-cl',c.colorLow);
  if(c.thresholdMid!=null){gid('c-tm').value=c.thresholdMid;gid('c-tmv').textContent=c.thresholdMid;}
  if(c.thresholdLow!=null){gid('c-tl').value=c.thresholdLow;gid('c-tlv').textContent=c.thresholdLow;}
  v('c-sid',c.soundId);
  if(c.soundVolume!=null){gid('c-sv2').value=c.soundVolume;gid('c-svv').textContent=parseFloat(c.soundVolume).toFixed(2);}
  if(c.soundPitch!=null){gid('c-sp').value=c.soundPitch;gid('c-spv').textContent=parseFloat(c.soundPitch).toFixed(2);}
}

async function saveSettings(){
  const p={
    positionPreset:gid('c-preset').value,scale:parseFloat(gid('c-scale').value),
    colorHigh:gid('c-ch').value,colorMid:gid('c-cm').value,colorLow:gid('c-cl').value,
    thresholdMid:parseInt(gid('c-tm').value),thresholdLow:parseInt(g('c-tl').value),
soundId:gid('c-sid').value,soundVolume:parseFloat(gid('c-sv2').value),soundPitch:parseFloat(gid('c-sp').value)
};
const r=await fetch('/api/config',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(p)});
const d=await r.json();
if(d.success)toast('Settings saved','ok');else toast(d.error||'Failed','er');
}
function loadHist(){
fetch('/api/history').then(r=>r.json()).then(data=>{
const el=gid('hlist');
if(!data||!data.length){el.innerHTML='<div class="empty">No history yet</div>';return;}
el.innerHTML=[...data].reverse().slice(0,150).map(h=>{
const rd=h.repeatsDone?`<span class="bdg b-run">×${h.repeatsDone}</span>`:'';
return `<div style="display:flex;align-items:center;gap:12px;padding:9px 0;border-bottom:1px solid var(--bd)">
<span style="color:var(--mu);font-size:.72rem;min-width:140px;flex-shrink:0">${esc(h.timestamp)}</span>
<span style="flex:1;font-size:.85rem;font-weight:600">${esc(h.name)}</span>
<span style="color:var(--mu);font-size:.8rem">${esc(h.duration)}</span>
<span class="bdg b-stp" style="font-size:.65rem">${esc(h.mode)}</span>${rd}
</div>`;
    }).join('');
  });
}
function tog(hdr){
hdr.querySelector('.chev').classList.toggle('op');
const b=hdr.nextElementSibling;b.classList.toggle('op');
}
function toast(msg,t){
const w=gid('tw'),el=document.createElement('div');
el.className='toast t-'+(t||'ok');el.textContent=msg;w.appendChild(el);
setTimeout(()=>{if(el.parentNode)el.parentNode.removeChild(el);},3200);
}
function g(id){return document.getElementById(id);}
window.addEventListener('load',()=>{
gid('hurl').textContent=location.href;
connect();
});
</script>
</body>
</html>
""";
    }
}