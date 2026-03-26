package com.mateof24.webpanel;

import com.google.gson.*;
import com.mateof24.OnTimeConstants;
import com.mateof24.api.TimerInfo;
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
import java.net.InetSocketAddress;
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
            httpServer.createContext("/events", this::serveSSE);
            httpServer.start();
            running = true;

            if (!listenersRegistered) {
                TimerEventBus.registerOnStart(info -> { if (running) broadcastSSE(buildEvent("START", info)); });
                TimerEventBus.registerOnFinish(info -> { if (running) broadcastSSE(buildEvent("FINISH", info)); });
                TimerEventBus.registerOnPause(info -> { if (running) broadcastSSE(buildEvent("PAUSE", info)); });
                TimerEventBus.registerOnResume(info -> { if (running) broadcastSSE(buildEvent("RESUME", info)); });
                TimerEventBus.registerOnTick(info -> { if (running) broadcastSSE(buildEvent("TICK", info)); });
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
    public String getAccessUrl() { return "http://localhost:" + port + "/"; }

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
            notifyOps(Component.literal("§e[OnTime] §fWebPanel has no connections. It will close in 1 minute."));
        }
        if (idle >= INACTIVITY_MS) {
            notifyOps(Component.literal("§e[OnTime] §fWebPanel closed due to inactivity."));
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
                });
                case "stop" -> TimerManager.getInstance().getActiveTimer().ifPresent(t -> {
                    TimerTickHandler.cancelCooldown();
                    t.resetRepeatsDone();
                    t.reset();
                    TimerManager.getInstance().clearActiveTimer();
                    TimerManager.getInstance().saveTimers();
                    Services.PLATFORM.sendTimerSyncPacket(mcServer, "", 0, 0, false, false, false);
                });
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
                    });
                }
            }
        });
    }

    private JsonObject buildStateJson() {
        JsonObject root = new JsonObject();
        JsonArray timers = new JsonArray();
        for (Timer t : TimerManager.getInstance().getAllTimers().values()) {
            timers.add(timerJson(t));
        }
        root.add("timers", timers);
        TimerManager.getInstance().getActiveTimer().ifPresent(t -> root.addProperty("active", t.getName()));
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
        json.addProperty("repeat", t.isRepeat());
        json.addProperty("repeatCount", t.getRepeatCount());
        json.addProperty("repeatsDone", t.getRepeatsDone());
        json.addProperty("nextTimer", t.getNextTimer() != null ? t.getNextTimer() : "");
        json.addProperty("hasCondition", t.hasCondition());
        if (t.hasCondition()) {
            json.addProperty("conditionObjective", t.getConditionObjective());
            json.addProperty("conditionScore", t.getConditionScore());
            json.addProperty("conditionTarget", t.getConditionTarget());
        }
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
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>OnTime Panel</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:'Segoe UI',system-ui,sans-serif;background:#111827;color:#e5e7eb;min-height:100vh}
.hdr{background:#1f2937;padding:14px 24px;display:flex;align-items:center;gap:10px;border-bottom:1px solid #374151}
.hdr h1{color:#f9fafb;font-size:1.2rem;font-weight:700}
.dot{width:9px;height:9px;border-radius:50%;background:#22c55e;flex-shrink:0}
.dot.off{background:#ef4444}
#cst{font-size:.8rem;color:#6b7280;margin-left:4px}
.wrap{max-width:1100px;margin:0 auto;padding:20px}
.tabs{display:flex;gap:4px;margin-bottom:20px}
.tab{padding:7px 18px;background:#1f2937;border:none;color:#9ca3af;cursor:pointer;border-radius:6px;font-size:.85rem;transition:.2s}
.tab.on{background:#374151;color:#f9fafb}
.pane{display:none}.pane.on{display:block}
.card{background:#1f2937;border-radius:10px;padding:18px;margin-bottom:14px;border:1px solid #374151}
.sct{font-size:.75rem;font-weight:700;text-transform:uppercase;letter-spacing:.05em;color:#6b7280;margin-bottom:12px}
.big-time{font-size:2.8rem;font-weight:800;font-variant-numeric:tabular-nums;letter-spacing:1px;text-align:center;margin:4px 0}
.tname-act{text-align:center;font-size:.78rem;color:#9ca3af;text-transform:uppercase;letter-spacing:.1em;margin-bottom:4px}
.bar{background:#374151;border-radius:3px;height:6px;overflow:hidden;margin:10px 0}
.fill{height:100%;border-radius:3px;transition:width .8s,background .5s}
.pct-lbl{text-align:center;font-size:.75rem;color:#6b7280;margin-bottom:10px}
.btns{display:flex;gap:6px;justify-content:center;flex-wrap:wrap}
.btn{padding:7px 18px;border:none;border-radius:6px;cursor:pointer;font-size:.82rem;font-weight:600;transition:.15s}
.b-y{background:#fbbf24;color:#1f2937}.b-y:hover{background:#f59e0b}
.b-r{background:#ef4444;color:#fff}.b-r:hover{background:#dc2626}
.b-g{background:#22c55e;color:#1f2937}.b-g:hover{background:#16a34a}
.b-x{background:#374151;color:#d1d5db}.b-x:hover{background:#4b5563}
.trow{display:flex;align-items:center;gap:10px;background:#111827;border-radius:7px;padding:10px 14px;margin-bottom:8px}
.trow:last-child{margin-bottom:0}
.ttime{font-variant-numeric:tabular-nums;font-weight:700;min-width:78px;font-size:.95rem}
.tinfo{flex:1}
.tname2{font-weight:600;font-size:.88rem}
.tmeta{font-size:.72rem;color:#6b7280;margin-top:2px}
.bdg{display:inline-block;padding:1px 7px;border-radius:4px;font-size:.68rem;font-weight:700;margin-left:5px;vertical-align:middle}
.bdg-run{background:#064e3b;color:#4ade80}
.bdg-stp{background:#1f2937;color:#6b7280;border:1px solid #374151}
.bdg-act{background:#7c2d12;color:#fb923c}
.empty{text-align:center;color:#4b5563;padding:30px;font-size:.9rem}
.hr{display:flex;align-items:flex-start;gap:12px;padding:9px 0;border-bottom:1px solid #374151}
.hr:last-child{border-bottom:none}
.hrt{color:#6b7280;font-size:.75rem;min-width:135px;padding-top:1px;flex-shrink:0}
.hrd strong{font-size:.88rem}.hrd span{color:#9ca3af;font-size:.82rem}
</style>
</head>
<body>
<div class="hdr">
  <div class="dot" id="dot"></div>
  <h1>⏱ OnTime Panel</h1>
  <span id="cst">Connecting...</span>
</div>
<div class="wrap">
  <div class="tabs">
    <button class="tab on" onclick="sw('timers',this)">Timers</button>
    <button class="tab" onclick="sw('history',this)">History</button>
  </div>
  <div id="pane-timers" class="pane on">
    <div class="card" id="acard"><div class="empty">No active timer</div></div>
    <div class="sct">All Timers</div>
    <div id="tlist"><div class="empty">Loading...</div></div>
  </div>
  <div id="pane-history" class="pane">
    <div class="card">
      <div class="sct">Completion History</div>
      <div id="hlist"><div class="empty">Loading...</div></div>
    </div>
  </div>
</div>
<script>
let S={timers:[],active:null},es=null;
const gc=p=>p>30?'#4ade80':p>10?'#fbbf24':'#ef4444';

function sw(name,btn){
  document.querySelectorAll('.pane').forEach(p=>p.classList.remove('on'));
  document.querySelectorAll('.tab').forEach(t=>t.classList.remove('on'));
  document.getElementById('pane-'+name).classList.add('on');
  btn.classList.add('on');
  if(name==='history')loadHistory();
}

function connect(){
  if(es)es.close();
  es=new EventSource('/events');
  es.addEventListener('INIT',e=>{const d=JSON.parse(e.data);S.timers=d.timers||[];S.active=d.active||null;render();});
  ['START','FINISH','PAUSE','RESUME'].forEach(ev=>{
    es.addEventListener(ev,e=>{
      const d=JSON.parse(e.data);
      if(ev==='START')S.active=d.name;
      if(ev==='FINISH'&&S.active===d.name)S.active=null;
      const i=S.timers.findIndex(t=>t.name===d.name);
      if(i>=0)S.timers[i]={...S.timers[i],...d};
      render();
    });
  });
  es.addEventListener('TICK',e=>{
    const d=JSON.parse(e.data);
    const i=S.timers.findIndex(t=>t.name===d.name);
    if(i>=0){S.timers[i]={...S.timers[i],...d};renderActive();}
  });
  es.onopen=()=>{document.getElementById('dot').className='dot';document.getElementById('cst').textContent='Connected';};
  es.onerror=()=>{document.getElementById('dot').className='dot off';document.getElementById('cst').textContent='Reconnecting...';setTimeout(connect,3000);};
}

function renderActive(){
  const el=document.getElementById('acard');
  const t=S.timers.find(t=>t.name===S.active);
  if(!t){el.innerHTML='<div class="empty">No active timer</div>';return;}
  const p=Math.max(0,Math.min(100,t.percentage)),c=gc(p);
  el.innerHTML=`<div class="tname-act">${t.name}</div>
<div class="big-time" style="color:${c}">${t.formattedTime}</div>
<div class="bar"><div class="fill" style="width:${p}%;background:${c}"></div></div>
<div class="pct-lbl">${p.toFixed(1)}% &mdash; ${t.countUp?'↑ count-up':'↓ countdown'}</div>
<div class="btns">
  <button class="btn b-y" onclick="act('pause')">${t.running?'Pause':'Resume'}</button>
  <button class="btn b-r" onclick="act('stop')">Stop</button>
  <button class="btn b-x" onclick="act('reset','${t.name}')">Reset</button>
</div>`;
}

function render(){
  renderActive();
  const el=document.getElementById('tlist');
  if(!S.timers.length){el.innerHTML='<div class="empty">No timers configured</div>';return;}
  el.innerHTML=S.timers.map(t=>{
    const isA=t.name===S.active;
    const bdg=isA?'<span class="bdg bdg-act">ACTIVE</span>':
              t.running?'<span class="bdg bdg-run">RUNNING</span>':
              '<span class="bdg bdg-stp">STOPPED</span>';
    const meta=[];
    if(t.repeat)meta.push(t.repeatCount===-1?'∞':'×'+t.repeatCount+' ('+t.repeatsDone+' done)');
    if(t.nextTimer)meta.push('→ '+t.nextTimer);
    if(t.hasCondition)meta.push('⚡ '+t.conditionObjective+'≥'+t.conditionScore);
    const sb=(!isA&&!t.running&&!S.active)?`<button class="btn b-g" style="padding:5px 12px;font-size:.78rem" onclick="act('start','${t.name}')">Start</button>`:'';
    const rb=`<button class="btn b-x" style="padding:5px 10px;font-size:.78rem" onclick="act('reset','${t.name}')">⟳</button>`;
    return `<div class="trow">
  <div class="ttime" style="color:${gc(t.percentage)}">${t.formattedTime}</div>
  <div class="tinfo"><div class="tname2">${t.name}${bdg}</div>${meta.length?`<div class="tmeta">${meta.join(' · ')}</div>`:''}</div>
  ${sb}${rb}
</div>`;
  }).join('');
}

function act(action,timer){
  fetch('/api/action',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action,timer:timer||null})});
}

function loadHistory(){
  fetch('/api/history').then(r=>r.json()).then(data=>{
    const el=document.getElementById('hlist');
    if(!data||!data.length){el.innerHTML='<div class="empty">No history yet</div>';return;}
    el.innerHTML=[...data].reverse().slice(0,100).map(h=>
      `<div class="hr"><div class="hrt">${h.timestamp}</div><div class="hrd"><strong>${h.name}</strong><span> &mdash; ${h.duration} (${h.mode})</span>${h.repeatsDone?`<span class="bdg bdg-run" style="margin-left:4px">×${h.repeatsDone}</span>`:''}</div></div>`
    ).join('');
  });
}

connect();
</script>
</body>
</html>
""";
    }
}