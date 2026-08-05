package com.mateof24.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mateof24.api.Audience;
import com.mateof24.api.OnTimeAPI;
import com.mateof24.api.RunMode;
import com.mateof24.api.TimerDefinition;
import com.mateof24.api.TimerRunInfo;
import com.mateof24.config.ModConfig;
import com.mateof24.config.TimerPositionPreset;
import com.mateof24.manager.DisplaySlots;
import com.mateof24.manager.TimerManager;
import com.mateof24.timer.Timer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Every administration operation, once.
 *
 * <p>The in-game panel and the web panel need exactly the same set of things
 * done. Implementing them twice guarantees the two drift, so they are
 * implemented here and both surfaces call in. Adding an operation is a case in
 * {@link #apply}, and both surfaces get it.</p>
 *
 * <p><b>Nothing here trusts its caller.</b> Every call carries who is asking,
 * every operation re-checks the permission, and every argument is validated
 * again — a client with the screen open has proved nothing, and a modified one
 * can send whatever packet it likes. The surfaces are transport; this is the
 * security boundary.</p>
 *
 * <p>Everything runs on the server thread. Surfaces that arrive on another one
 * (the web panel's HTTP threads) hand over with {@code server.execute(...)}
 * first.</p>
 */
public final class AdminOps {

    private AdminOps() {}

    /** Permission node gating the whole surface. */
    public static final String NODE = "ontime.command.gui";

    /** Outcome of one operation: what to tell the caller, and whether to re-push state. */
    public record Result(boolean success, String message, boolean stateChanged) {

        public static Result ok() { return new Result(true, null, true); }

        public static Result ok(String message) { return new Result(true, message, true); }

        /** Succeeded but changed nothing worth re-pushing. */
        public static Result quiet() { return new Result(true, null, false); }

        public static Result fail(String message) { return new Result(false, message, false); }
    }

    /**
     * Who is asking. Not a {@code ServerPlayer} because the web panel has no
     * player — it authenticated with a token instead, and the two arrive at the
     * same operations by different doors.
     */
    public record Caller(ServerPlayer player, String label) {

        public static Caller of(ServerPlayer player) {
            return new Caller(player, player.getName().getString());
        }

        /** An already-authenticated web-panel session. */
        public static Caller web(String label) {
            return new Caller(null, label);
        }

        /**
         * A player must hold the node; a web session is only constructed after
         * its token checked out, so it is trusted at this point by
         * construction.
         */
        public boolean allowed() {
            if (player == null) return true;
            return com.mateof24.permission.PermissionHelper.hasPermission(
                    player.createCommandSourceStack(), NODE, 4);
        }
    }

    // ==================================================================
    // Dispatch
    // ==================================================================

    /**
     * Runs one operation.
     *
     * @param op   the operation name, as it travels over the wire
     * @param args its arguments; missing or malformed ones are rejected here
     */
    public static Result apply(MinecraftServer server, Caller caller, String op, JsonObject args) {
        if (!caller.allowed()) return Result.fail("Not permitted");
        if (op == null) return Result.fail("Missing operation");
        if (args == null) args = new JsonObject();

        try {
            return switch (op) {
                case "timer.create" -> createTimer(args);
                case "timer.delete" -> deleteTimer(args);
                case "timer.clone" -> cloneTimer(args);
                case "timer.setTime" -> setTime(args);
                case "timer.addTime" -> addTime(args);
                case "timer.setCommand" -> setCommand(args);
                case "timer.setTitle" -> setTitle(args);
                case "timer.setRepeat" -> setRepeat(args);
                case "timer.setSequence" -> setSequence(args);
                case "timer.setPosition" -> setPosition(args);
                case "timer.setScale" -> setScale(args);
                case "timer.setSilent" -> setSilent(args);

                case "run.start" -> startRun(server, args);
                case "run.pause" -> runAction(args, OnTimeAPI.getInstance()::pauseRun, "It is already paused");
                case "run.resume" -> runAction(args, OnTimeAPI.getInstance()::resumeRun, "It is already running");
                case "run.stop" -> runAction(args, OnTimeAPI.getInstance()::stopRun, "Could not stop it");
                case "run.reset" -> runAction(args, OnTimeAPI.getInstance()::resetRun, "Could not reset it");
                case "run.stopAll" -> Result.ok(OnTimeAPI.getInstance().stopAllRuns() + " run(s) stopped");
                case "run.setAudience" -> setAudience(args);

                case "config.set" -> setConfig(args);

                default -> Result.fail("Unknown operation: " + op);
            };
        } catch (Exception e) {
            com.mateof24.OnTimeConstants.LOGGER.warn("[OnTime/Admin] '{}' from {} failed", op, caller.label(), e);
            return Result.fail("Operation failed: " + e.getClass().getSimpleName());
        }
    }

    // ==================================================================
    // State
    // ==================================================================

    /**
     * Everything a panel draws, as one JSON document.
     *
     * <p>A whole snapshot rather than deltas: it is self-healing, a panel that
     * misses a message corrects itself on the next one, and at this size —
     * a few kilobytes for a server with dozens of timers — the difference is
     * not worth the bookkeeping.</p>
     */
    public static JsonObject state(MinecraftServer server) {
        JsonObject root = new JsonObject();

        JsonArray timers = new JsonArray();
        for (TimerDefinition def : OnTimeAPI.getInstance().getDefinitions()) timers.add(timerJson(def));
        root.add("timers", timers);

        JsonArray runs = new JsonArray();
        for (TimerRunInfo run : OnTimeAPI.getInstance().getRuns()) runs.add(runJson(server, run));
        root.add("runs", runs);

        root.add("config", configJson());
        root.add("players", playersJson(server));
        root.add("presets", presetsJson());
        return root;
    }

    private static JsonObject timerJson(TimerDefinition def) {
        JsonObject json = new JsonObject();
        json.addProperty("name", def.name());
        json.addProperty("targetTicks", def.targetTicks());
        json.addProperty("countUp", def.countUp());
        json.addProperty("silent", def.silent());
        json.addProperty("finishCommand", def.finishCommand());
        json.add("finishCommands", toArray(def.finishCommands()));

        JsonArray scheduled = new JsonArray();
        def.scheduledCommands().forEach((at, commands) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("at", at);
            entry.add("commands", toArray(commands));
            scheduled.add(entry);
        });
        json.add("scheduled", scheduled);

        JsonObject titles = new JsonObject();
        def.titles().forEach(titles::addProperty);
        json.add("titles", titles);

        json.addProperty("repeat", def.repeat());
        json.addProperty("repeatCount", def.repeatCount());
        json.addProperty("repeatCooldownTicks", def.repeatCooldownTicks());
        json.addProperty("nextTimer", def.nextTimer());
        json.addProperty("sequenceCooldownTicks", def.sequenceCooldownTicks());
        json.addProperty("conditionObjective", def.conditionObjective());
        json.addProperty("conditionScore", def.conditionScore());
        json.addProperty("conditionTarget", def.conditionTarget());
        json.addProperty("conditionAction", def.conditionAction());
        json.addProperty("conditionExpression", def.conditionExpression());
        json.addProperty("conditionExpressionAction", def.conditionExpressionAction());
        json.addProperty("triggerType", def.triggerType());
        json.addProperty("triggerAction", def.triggerAction());
        json.addProperty("position", def.position());
        json.addProperty("customX", def.customX());
        json.addProperty("customY", def.customY());
        json.addProperty("scale", def.scale());
        TimerManager.getInstance().getTimer(def.name()).ifPresent(timer -> {
            json.addProperty("resolvedPreset", DisplaySlots.presetOf(timer));
            json.add("display", timer.display().toJson());
        });
        json.addProperty("runCount", OnTimeAPI.getInstance().getRunsOf(def.name()).size());
        return json;
    }

    private static JsonObject runJson(MinecraftServer server, TimerRunInfo run) {
        JsonObject json = new JsonObject();
        json.addProperty("runId", run.runId().toString());
        json.addProperty("timerName", run.timerName());
        json.addProperty("currentTicks", run.currentTicks());
        json.addProperty("targetTicks", run.targetTicks());
        json.addProperty("countUp", run.countUp());
        json.addProperty("running", run.running());
        json.addProperty("mode", run.mode().name());
        json.addProperty("phase", run.phase().name());
        json.addProperty("repeatsDone", run.repeatsDone());
        if (run.owner() != null) {
            json.addProperty("owner", run.owner().toString());
            json.addProperty("ownerName", playerName(server, run.owner()));
        }
        json.addProperty("audienceScope", run.audience().scope().name());
        JsonArray members = new JsonArray();
        for (UUID id : run.audience().players()) {
            JsonObject member = new JsonObject();
            member.addProperty("uuid", id.toString());
            member.addProperty("name", playerName(server, id));
            members.add(member);
        }
        json.add("audience", members);
        TimerManager.getInstance().getTimer(run.timerName())
                .ifPresent(timer -> json.add("display", timer.display().toJson()));
        return json;
    }

    private static JsonObject configJson() {
        ModConfig config = ModConfig.getInstance();
        JsonObject json = new JsonObject();
        json.addProperty("positionPreset", config.getPositionPreset().name());
        json.addProperty("timerX", config.getTimerX());
        json.addProperty("timerY", config.getTimerY());
        json.addProperty("timerScale", config.getTimerScale());
        json.addProperty("colorHigh", config.getColorHigh());
        json.addProperty("colorMid", config.getColorMid());
        json.addProperty("colorLow", config.getColorLow());
        json.addProperty("thresholdMid", config.getThresholdMid());
        json.addProperty("thresholdLow", config.getThresholdLow());
        json.addProperty("timerSoundId", config.getTimerSoundId());
        json.addProperty("timerSoundVolume", config.getTimerSoundVolume());
        json.addProperty("timerSoundPitch", config.getTimerSoundPitch());
        json.addProperty("maxTimerSeconds", config.getMaxTimerSeconds());
        json.addProperty("commandDelayTicks", config.getCommandDelayTicks());
        json.addProperty("confirmRunThreshold", config.getConfirmRunThreshold());
        json.addProperty("webSocketEnabled", config.isWebSocketEnabled());
        json.addProperty("webSocketPort", config.getWebSocketPort());
        json.addProperty("webPanelPort", config.getWebPanelPort());
        return json;
    }

    private static JsonArray playersJson(MinecraftServer server) {
        JsonArray players = new JsonArray();
        if (server == null) return players;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            JsonObject json = new JsonObject();
            json.addProperty("uuid", player.getUUID().toString());
            json.addProperty("name", player.getName().getString());
            String team = player.getTeam() != null ? player.getTeam().getName() : null;
            json.addProperty("team", team);
            players.add(json);
        }
        return players;
    }

    private static JsonArray presetsJson() {
        JsonArray presets = new JsonArray();
        for (TimerPositionPreset preset : TimerPositionPreset.values()) {
            JsonObject json = new JsonObject();
            json.addProperty("name", preset.name());
            json.addProperty("display", preset.getDisplayName());
            json.addProperty("anchor", preset != TimerPositionPreset.CUSTOM);
            presets.add(json);
        }
        return presets;
    }

    // ==================================================================
    // Definition operations
    // ==================================================================

    private static Result createTimer(JsonObject args) {
        String name = str(args, "name");
        if (name == null || !name.matches("[A-Za-z0-9_.+-]{1,32}")) {
            return Result.fail("Invalid name");
        }
        int h = clamp(intOf(args, "hours", 0), 0, 1000);
        int m = clamp(intOf(args, "minutes", 0), 0, 59);
        int s = clamp(intOf(args, "seconds", 0), 0, 59);
        if (h == 0 && m == 0 && s == 0) return Result.fail("Duration cannot be zero");

        long total = h * 3600L + m * 60L + s;
        long max = ModConfig.getInstance().getMaxTimerSeconds();
        if (total > max) return Result.fail("Longer than the server maximum of " + max + "s");

        if (!OnTimeAPI.getInstance().createTimer(name, h, m, s, bool(args, "countUp", false))) {
            return Result.fail("A timer called '" + name + "' already exists");
        }
        return Result.ok("Created '" + name + "'");
    }

    private static Result deleteTimer(JsonObject args) {
        String name = requireTimer(args);
        if (name == null) return Result.fail("No such timer");
        OnTimeAPI.getInstance().removeTimer(name);
        return Result.ok("Deleted '" + name + "'");
    }

    private static Result cloneTimer(JsonObject args) {
        String source = requireTimer(args);
        String dest = str(args, "dest");
        if (source == null) return Result.fail("No such timer");
        if (dest == null || !dest.matches("[A-Za-z0-9_.+-]{1,32}")) return Result.fail("Invalid name");
        if (TimerManager.getInstance().hasTimer(dest)) return Result.fail("A timer called '" + dest + "' already exists");

        // Same route as /timer clone: round-trip the JSON and reset the copy's
        // run state, so a clone never inherits a half-finished clock.
        JsonObject json = TimerManager.getInstance().getTimer(source).orElseThrow().toJson();
        json.addProperty("name", dest);
        json.addProperty("running", false);
        json.addProperty("wasRunningBeforeShutdown", false);
        json.addProperty("repeatsDone", 0);
        json.addProperty("currentTicks",
                json.get("countUp").getAsBoolean() ? 0 : json.get("targetTicks").getAsLong());

        Timer copy = Timer.fromJson(json);
        if (copy == null || !TimerManager.getInstance().addTimer(copy)) {
            return Result.fail("Could not copy '" + source + "'");
        }
        return Result.ok("Cloned '" + source + "' to '" + dest + "'");
    }

    private static Result setTime(JsonObject args) {
        String name = requireTimer(args);
        if (name == null) return Result.fail("No such timer");
        int h = clamp(intOf(args, "hours", 0), 0, 1000);
        int m = clamp(intOf(args, "minutes", 0), 0, 59);
        int s = clamp(intOf(args, "seconds", 0), 0, 59);
        long max = ModConfig.getInstance().getMaxTimerSeconds();
        if (h * 3600L + m * 60L + s > max) return Result.fail("Longer than the server maximum of " + max + "s");
        return OnTimeAPI.getInstance().setTimerTime(name, h, m, s) ? Result.ok() : Result.fail("Could not set the time");
    }

    private static Result addTime(JsonObject args) {
        String name = requireTimer(args);
        if (name == null) return Result.fail("No such timer");
        int h = clamp(intOf(args, "hours", 0), 0, 1000);
        int m = clamp(intOf(args, "minutes", 0), 0, 59);
        int s = clamp(intOf(args, "seconds", 0), 0, 59);
        return OnTimeAPI.getInstance().addTimerTime(name, h, m, s) ? Result.ok() : Result.fail("Could not add time");
    }

    private static Result setCommand(JsonObject args) {
        String name = requireTimer(args);
        if (name == null) return Result.fail("No such timer");
        String command = str(args, "command");
        if (command != null && !command.isEmpty()) {
            var validation = com.mateof24.validation.CommandValidator.validate(command);
            if (!validation.isValid()) return Result.fail(validation.getErrorMessage().getString());
        }
        return OnTimeAPI.getInstance().setTimerCommand(name, command == null ? "" : command)
                ? Result.ok() : Result.fail("Could not set the command");
    }

    private static Result setTitle(JsonObject args) {
        String name = requireTimer(args);
        if (name == null) return Result.fail("No such timer");
        String slot = str(args, "slot");
        if (slot == null) return Result.fail("Missing slot");
        return OnTimeAPI.getInstance().setTimerTitle(name, slot, str(args, "text"))
                ? Result.ok() : Result.fail("Invalid slot or title");
    }

    private static Result setRepeat(JsonObject args) {
        String name = requireTimer(args);
        if (name == null) return Result.fail("No such timer");
        Timer timer = TimerManager.getInstance().getTimer(name).orElseThrow();
        boolean repeat = bool(args, "repeat", false);
        timer.setRepeat(repeat);
        timer.setRepeatCount(Math.max(-1, intOf(args, "count", -1)));
        timer.setRepeatCooldownTicks(Math.max(0, intOf(args, "cooldownSeconds", 0)) * 20L);
        TimerManager.getInstance().saveTimer(timer);
        return Result.ok();
    }

    private static Result setSequence(JsonObject args) {
        String name = requireTimer(args);
        if (name == null) return Result.fail("No such timer");
        String next = str(args, "next");
        if (next != null && !next.isEmpty()) {
            if (next.equals(name)) return Result.fail("A timer cannot follow itself");
            if (!TimerManager.getInstance().hasTimer(next)) return Result.fail("No timer called '" + next + "'");
        }
        Timer timer = TimerManager.getInstance().getTimer(name).orElseThrow();
        timer.setNextTimer(next == null || next.isEmpty() ? null : next);
        timer.setSequenceCooldownTicks(Math.max(0, intOf(args, "cooldownSeconds", 0)) * 20L);
        TimerManager.getInstance().saveTimer(timer);
        return Result.ok();
    }

    private static Result setPosition(JsonObject args) {
        String name = requireTimer(args);
        if (name == null) return Result.fail("No such timer");
        String preset = str(args, "preset");

        if (preset != null && TimerPositionPreset.parse(preset) == TimerPositionPreset.CUSTOM) {
            return OnTimeAPI.getInstance().setTimerCustomPosition(
                    name, intOf(args, "x", 0), Math.max(0, intOf(args, "y", 0)))
                    ? Result.ok() : Result.fail("Could not pin the timer");
        }
        if (preset != null && TimerPositionPreset.parse(preset) == null) return Result.fail("Unknown preset");

        return OnTimeAPI.getInstance().setTimerPosition(name, preset)
                ? Result.ok()
                : Result.fail("That slot is taken for someone who would see both timers");
    }

    private static Result setScale(JsonObject args) {
        String name = requireTimer(args);
        if (name == null) return Result.fail("No such timer");
        Float scale = args.has("scale") && !args.get("scale").isJsonNull()
                ? args.get("scale").getAsFloat() : null;
        if (scale != null && (scale < 0.1f || scale > 5.0f)) return Result.fail("Scale must be between 0.1 and 5");
        return OnTimeAPI.getInstance().setTimerScale(name, scale) ? Result.ok() : Result.fail("Could not set the scale");
    }

    private static Result setSilent(JsonObject args) {
        String name = requireTimer(args);
        if (name == null) return Result.fail("No such timer");
        Timer timer = TimerManager.getInstance().getTimer(name).orElseThrow();
        timer.setSilent(bool(args, "silent", false));
        TimerManager.getInstance().saveTimer(timer);
        com.mateof24.network.TimerState.markDirty();
        return Result.ok();
    }

    // ==================================================================
    // Execution operations
    // ==================================================================

    private static Result startRun(MinecraftServer server, JsonObject args) {
        String name = requireTimer(args);
        if (name == null) return Result.fail("No such timer");

        RunMode mode = "each".equalsIgnoreCase(str(args, "mode")) ? RunMode.EACH : RunMode.SHARED;
        Audience audience = readAudience(server, args);
        if (audience == null) return Result.fail("No players matched");

        // The count gate of F3.2, applied here too: a panel is a place where
        // one careless click creates a hundred clocks.
        if (mode == RunMode.EACH) {
            int wouldCreate = 0;
            for (UUID player : audience.players()) {
                if (TimerManager.getInstance().findOverlapping(name, Audience.ofPlayer(player)) == null) wouldCreate++;
            }
            int threshold = ModConfig.getInstance().getConfirmRunThreshold();
            if (threshold >= 0 && wouldCreate >= threshold && !bool(args, "confirmed", false)) {
                return Result.fail("CONFIRM:" + wouldCreate);
            }
        }

        List<UUID> created = OnTimeAPI.getInstance().startRun(name, audience, mode);
        if (created.isEmpty()) {
            return Result.fail("Could not start: already running for those players, or its slot is taken");
        }
        return Result.ok("Started " + created.size() + " run(s) of '" + name + "'");
    }

    /**
     * @param failure what to say when the run exists but the action did
     *                nothing. Existence is checked separately so that an
     *                unknown id never reports "already paused", which is both
     *                false and the kind of thing a panel author debugs for an
     *                hour.
     */
    private static Result runAction(JsonObject args, java.util.function.Predicate<UUID> action, String failure) {
        if (!args.has("runId")) return Result.fail("Missing run id");
        UUID runId = uuid(args, "runId");
        if (runId == null) return Result.fail("Malformed run id");
        if (OnTimeAPI.getInstance().getRun(runId).isEmpty()) return Result.fail("No such run");
        return action.test(runId) ? Result.ok() : Result.fail(failure);
    }

    private static Result setAudience(JsonObject args) {
        UUID runId = uuid(args, "runId");
        if (runId == null) return Result.fail("Missing run id");
        if (OnTimeAPI.getInstance().getRun(runId).isEmpty()) return Result.fail("No such run");

        Set<UUID> players = readPlayerIds(args);
        if (players == null) return Result.fail("Missing players");
        Audience audience = bool(args, "global", false) ? Audience.global() : Audience.ofPlayers(players);
        return OnTimeAPI.getInstance().setRunAudience(runId, audience)
                ? Result.ok()
                : Result.fail("Someone there already has a run of this timer");
    }

    // ==================================================================
    // Config operations
    // ==================================================================

    /**
     * Writes one config key. One key at a time on purpose: a panel that sends
     * the whole config back would silently undo a change another admin made
     * between the state it drew and the button that was pressed.
     */
    private static Result setConfig(JsonObject args) {
        String key = str(args, "key");
        if (key == null) return Result.fail("Missing key");
        ModConfig config = ModConfig.getInstance();

        switch (key) {
            case "positionPreset" -> {
                TimerPositionPreset preset = TimerPositionPreset.parse(str(args, "value"));
                if (preset == null) return Result.fail("Unknown preset");
                config.setPositionPreset(preset);
            }
            case "timerX" -> config.setTimerX(intOf(args, "value", 0));
            case "timerY" -> config.setTimerY(Math.max(0, intOf(args, "value", 0)));
            case "timerScale" -> {
                float scale = (float) args.get("value").getAsDouble();
                if (scale < 0.1f || scale > 5.0f) return Result.fail("Scale must be between 0.1 and 5");
                config.setTimerScale(scale);
            }
            case "colorHigh" -> config.setColorHigh(intOf(args, "value", 0xFFFFFF));
            case "colorMid" -> config.setColorMid(intOf(args, "value", 0xFFFF00));
            case "colorLow" -> config.setColorLow(intOf(args, "value", 0xFF0000));
            case "thresholdMid" -> config.setThresholdMid(clamp(intOf(args, "value", 30), 0, 100));
            case "thresholdLow" -> config.setThresholdLow(clamp(intOf(args, "value", 10), 0, 100));
            case "timerSoundId" -> {
                String id = str(args, "value");
                if (id == null || !id.matches("[a-z0-9_.-]+(:[a-z0-9_./-]+)?")) return Result.fail("Invalid sound id");
                config.setTimerSoundId(id);
            }
            case "timerSoundVolume" -> config.setTimerSoundVolume(clampF((float) args.get("value").getAsDouble(), 0f, 1f));
            case "timerSoundPitch" -> config.setTimerSoundPitch(clampF((float) args.get("value").getAsDouble(), 0.5f, 2f));
            case "maxTimerSeconds" -> config.setMaxTimerSeconds(Math.max(1, args.get("value").getAsLong()));
            case "commandDelayTicks" -> config.setCommandDelayTicks(clamp(intOf(args, "value", 0), 0, 1200));
            case "confirmRunThreshold" -> config.setConfirmRunThreshold(Math.max(-1, intOf(args, "value", 8)));
            case "webSocketEnabled" -> config.setWebSocketEnabled(bool(args, "value", false));
            case "webSocketPort" -> config.setWebSocketPort(clamp(intOf(args, "value", 25581), 1024, 65535));
            case "webPanelPort" -> config.setWebPanelPort(clamp(intOf(args, "value", 25580), 1024, 65535));
            default -> {
                return Result.fail("Unknown setting: " + key);
            }
        }
        config.save();
        com.mateof24.network.TimerState.markDirty();
        return Result.ok();
    }

    // ==================================================================
    // Argument reading — every one of these tolerates missing and wrong types
    // ==================================================================

    private static String requireTimer(JsonObject args) {
        String name = str(args, "name");
        return name != null && TimerManager.getInstance().hasTimer(name) ? name : null;
    }

    /** @return null when the request names players and none of them resolved */
    private static Audience readAudience(MinecraftServer server, JsonObject args) {
        if (bool(args, "global", false)) return Audience.global();
        Set<UUID> players = readPlayerIds(args);
        if (players == null || players.isEmpty()) return null;
        return Audience.ofPlayers(players);
    }

    private static Set<UUID> readPlayerIds(JsonObject args) {
        if (!args.has("players") || !args.get("players").isJsonArray()) return null;
        Set<UUID> players = new LinkedHashSet<>();
        for (var element : args.getAsJsonArray("players")) {
            try { players.add(UUID.fromString(element.getAsString())); } catch (Exception ignored) {}
        }
        return players;
    }

    private static String playerName(MinecraftServer server, UUID id) {
        if (server == null) return id.toString().substring(0, 8);
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        return player != null ? player.getName().getString() : id.toString().substring(0, 8);
    }

    private static String str(JsonObject args, String key) {
        if (!args.has(key) || args.get(key).isJsonNull()) return null;
        try { return args.get(key).getAsString(); } catch (Exception e) { return null; }
    }

    private static int intOf(JsonObject args, String key, int fallback) {
        if (!args.has(key) || args.get(key).isJsonNull()) return fallback;
        try { return args.get(key).getAsInt(); } catch (Exception e) { return fallback; }
    }

    private static boolean bool(JsonObject args, String key, boolean fallback) {
        if (!args.has(key) || args.get(key).isJsonNull()) return fallback;
        try { return args.get(key).getAsBoolean(); } catch (Exception e) { return fallback; }
    }

    private static UUID uuid(JsonObject args, String key) {
        String raw = str(args, key);
        if (raw == null) return null;
        try { return UUID.fromString(raw); } catch (Exception e) { return null; }
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private static float clampF(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }

    private static JsonArray toArray(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) array.add(value);
        return array;
    }

    /** Names of every operation {@link #apply} understands, for tests and docs. */
    public static List<String> operations() {
        return List.of("timer.create", "timer.delete", "timer.clone", "timer.setTime", "timer.addTime",
                "timer.setCommand", "timer.setTitle", "timer.setRepeat", "timer.setSequence",
                "timer.setPosition", "timer.setScale", "timer.setSilent",
                "run.start", "run.pause", "run.resume", "run.stop", "run.reset", "run.stopAll",
                "run.setAudience", "config.set");
    }
}
