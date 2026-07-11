package com.mateof24.timer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Timer {

    /** Sanity caps for scheduled commands (4.0.0). */
    public static final int MAX_SCHEDULED_ENTRIES = 64;
    public static final int MAX_COMMANDS_PER_POINT = 16;

    /** Commands fired when the displayed time crosses {@code atSeconds} (4.0.0). */
    public static final class CommandEvent {
        private final long atSeconds;
        private final List<String> commands = new ArrayList<>();

        public CommandEvent(long atSeconds) { this.atSeconds = atSeconds; }
        public long getAtSeconds() { return atSeconds; }
        public List<String> getCommands() { return commands; }
    }

    /**
     * One row of the flattened, user-facing enumeration used by
     * {@code /timer commands <name> list|remove}: timed entries first
     * (events sorted by time ascending, commands in insertion order),
     * then finish commands. {@code atSeconds == null} means "on finish".
     */
    public record ScheduledEntry(Long atSeconds, String command) {}

    private final String name;
    private long currentTicks;
    private final long targetTicks;
    private final boolean countUp;
    private boolean running;
    private String command;
    private boolean silent;
    private boolean wasRunningBeforeShutdown;
    private boolean repeat = false;
    private int repeatCount = -1;
    private int repeatsDone = 0;
    private String nextTimer = null;
    private String conditionObjective = null;
    private int conditionScore = 0;
    private String conditionTarget = "*";
    private long repeatCooldownTicks = 0L;
    private long sequenceCooldownTicks = 0L;
    private String conditionExpression = null;
    private String triggerType = null;
    private String conditionExpressionAction = "finish";
    private String scoreConditionAction = "finish";
    private String triggerAction = "finish";
    // Scheduled commands (4.0.0). commandEvents is kept sorted by atSeconds
    // ascending; the legacy single 'command' field is untouched by these.
    private final List<CommandEvent> commandEvents = new ArrayList<>();
    private final List<String> finishCommands = new ArrayList<>();

    public Timer(String name, int hours, int minutes, int seconds, boolean countUp) {
        this.name = name;
        this.targetTicks = (hours * 3600L + minutes * 60L + seconds) * 20L;
        this.countUp = countUp;
        this.currentTicks = countUp ? 0 : this.targetTicks;
        this.running = false;
        this.command = com.mateof24.command.PlaceholderSystem.DEFAULT_COMMAND;
        this.silent = false;
        this.wasRunningBeforeShutdown = false;
    }

    public boolean tick() {
        if (!running) return false;

        if (countUp) {
            currentTicks++;
            if (currentTicks >= targetTicks) {
                reset();
                return true;
            }
        } else {
            currentTicks--;
            if (currentTicks <= 0) {
                reset();
                return true;
            }
        }
        return false;
    }

    public void reset() {
        currentTicks = countUp ? 0 : targetTicks;
        running = false;
    }

    public void setTime(int hours, int minutes, int seconds) {
        currentTicks = (hours * 3600L + minutes * 60L + seconds) * 20L;
    }

    public void addTime(int hours, int minutes, int seconds) {
        long additionalTicks = (hours * 3600L + minutes * 60L + seconds) * 20L;
        currentTicks += additionalTicks;

        if (countUp && currentTicks > targetTicks) {
            currentTicks = targetTicks;
        } else if (!countUp && currentTicks < 0) {
            currentTicks = 0;
        }
    }

    public String getFormattedTime() {
        long totalSeconds = currentTicks / 20L;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("currentTicks", currentTicks);
        json.addProperty("targetTicks", targetTicks);
        json.addProperty("countUp", countUp);
        json.addProperty("running", running);
        json.addProperty("command", command != null ? command : "");
        json.addProperty("silent", silent);
        json.addProperty("wasRunningBeforeShutdown", wasRunningBeforeShutdown);
        json.addProperty("repeat", repeat);
        json.addProperty("repeatCount", repeatCount);
        json.addProperty("repeatsDone", repeatsDone);
        json.addProperty("nextTimer", nextTimer != null ? nextTimer : "");
        json.addProperty("conditionObjective", conditionObjective != null ? conditionObjective : "");
        json.addProperty("conditionScore", conditionScore);
        json.addProperty("conditionTarget", conditionTarget != null ? conditionTarget : "*");
        json.addProperty("repeatCooldownTicks", repeatCooldownTicks);
        json.addProperty("sequenceCooldownTicks", sequenceCooldownTicks);
        json.addProperty("conditionExpression", conditionExpression != null ? conditionExpression : "");
        json.addProperty("triggerType", triggerType != null ? triggerType : "");
        json.addProperty("conditionExpressionAction", conditionExpressionAction);
        json.addProperty("scoreConditionAction", scoreConditionAction);
        json.addProperty("triggerAction", triggerAction);
        JsonArray events = new JsonArray();
        for (CommandEvent event : commandEvents) {
            JsonObject e = new JsonObject();
            e.addProperty("atSeconds", event.getAtSeconds());
            JsonArray cmds = new JsonArray();
            for (String c : event.getCommands()) cmds.add(c);
            e.add("commands", cmds);
            events.add(e);
        }
        json.add("commandEvents", events);
        JsonArray finish = new JsonArray();
        for (String c : finishCommands) finish.add(c);
        json.add("finishCommands", finish);
        return json;
    }

    public static Timer fromJson(JsonObject json) {
        String name = json.get("name").getAsString();
        long targetTicks = json.get("targetTicks").getAsLong();
        boolean countUp = json.get("countUp").getAsBoolean();

        int totalSeconds = (int) (targetTicks / 20L);
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        Timer timer = new Timer(name, hours, minutes, seconds, countUp);
        timer.currentTicks = json.get("currentTicks").getAsLong();
        timer.running = json.get("running").getAsBoolean();
        timer.command = json.has("command") && !json.get("command").isJsonNull()
                ? json.get("command").getAsString()
                : com.mateof24.command.PlaceholderSystem.DEFAULT_COMMAND;
        timer.silent = json.has("silent") && json.get("silent").getAsBoolean();
        timer.wasRunningBeforeShutdown = json.has("wasRunningBeforeShutdown")
                && json.get("wasRunningBeforeShutdown").getAsBoolean();
        timer.repeat = json.has("repeat") && json.get("repeat").getAsBoolean();
        timer.repeatCount = json.has("repeatCount") ? json.get("repeatCount").getAsInt() : -1;
        timer.repeatsDone = json.has("repeatsDone") ? json.get("repeatsDone").getAsInt() : 0;
        timer.nextTimer = json.has("nextTimer") ? json.get("nextTimer").getAsString() : "";
        if (timer.nextTimer.isEmpty()) timer.nextTimer = null;
        String condObj = json.has("conditionObjective") ? json.get("conditionObjective").getAsString() : "";
        timer.conditionObjective = condObj.isEmpty() ? null : condObj;
        timer.conditionScore = json.has("conditionScore") ? json.get("conditionScore").getAsInt() : 0;
        timer.conditionTarget = json.has("conditionTarget") ? json.get("conditionTarget").getAsString() : "*";
        timer.repeatCooldownTicks = json.has("repeatCooldownTicks") ? json.get("repeatCooldownTicks").getAsLong() : 0L;
        timer.sequenceCooldownTicks = json.has("sequenceCooldownTicks") ? json.get("sequenceCooldownTicks").getAsLong() : 0L;
        String condExpr = json.has("conditionExpression") ? json.get("conditionExpression").getAsString() : "";
        timer.conditionExpression = condExpr.isEmpty() ? null : condExpr;
        String trig = json.has("triggerType") ? json.get("triggerType").getAsString() : "";
        timer.triggerType = trig.isEmpty() ? null : trig;
        timer.conditionExpressionAction = json.has("conditionExpressionAction") ? json.get("conditionExpressionAction").getAsString() : "finish";
        timer.scoreConditionAction = json.has("scoreConditionAction") ? json.get("scoreConditionAction").getAsString() : "finish";
        timer.triggerAction = json.has("triggerAction") ? json.get("triggerAction").getAsString() : "finish";

        // Scheduled commands (absent in pre-4.0.0 files = empty). Malformed
        // entries are skipped instead of failing the whole timer.
        if (json.has("commandEvents") && json.get("commandEvents").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("commandEvents")) {
                if (!el.isJsonObject()) continue;
                JsonObject e = el.getAsJsonObject();
                if (!e.has("atSeconds") || !e.has("commands") || !e.get("commands").isJsonArray()) continue;
                long at;
                try { at = e.get("atSeconds").getAsLong(); } catch (Exception ex) { continue; }
                if (at <= 0) continue;
                for (JsonElement c : e.getAsJsonArray("commands")) {
                    try { timer.addScheduledCommand(at, c.getAsString()); } catch (Exception ignored) {}
                }
            }
        }
        if (json.has("finishCommands") && json.get("finishCommands").isJsonArray()) {
            for (JsonElement c : json.getAsJsonArray("finishCommands")) {
                try { timer.addFinishCommand(c.getAsString()); } catch (Exception ignored) {}
            }
        }

        return timer;
    }

    public String getName() { return name; }
    public long getCurrentTicks() { return currentTicks; }
    public long getTargetTicks() { return targetTicks; }
    public boolean isCountUp() { return countUp; }
    public boolean isRunning() { return running; }
    public void setRunning(boolean running) { this.running = running; }
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command != null ? command : ""; }
    public void setCurrentTicks(long ticks) { this.currentTicks = ticks; }
    public boolean isSilent() { return silent; }
    public void setSilent(boolean silent) { this.silent = silent; }
    public boolean wasRunningBeforeShutdown() { return wasRunningBeforeShutdown; }
    public void setWasRunningBeforeShutdown(boolean was) { this.wasRunningBeforeShutdown = was; }
    public boolean isRepeat() { return repeat; }
    public void setRepeat(boolean repeat) { this.repeat = repeat; }
    public int getRepeatCount() { return repeatCount; }
    public void setRepeatCount(int count) { this.repeatCount = count; }
    public int getRepeatsDone() { return repeatsDone; }
    public void incrementRepeatsDone() { repeatsDone++; }
    public void resetRepeatsDone() { repeatsDone = 0; }
    public boolean shouldRepeatAgain() {
        if (!repeat) return false;
        if (repeatCount == -1) return true;
        return repeatsDone < repeatCount;
    }
    public String getNextTimer() { return nextTimer; }
    public void setNextTimer(String nextTimer) {
        this.nextTimer = (nextTimer == null || nextTimer.isEmpty()) ? null : nextTimer;
    }
    public String getConditionObjective() { return conditionObjective; }
    public int getConditionScore() { return conditionScore; }
    public String getConditionTarget() { return conditionTarget; }
    public boolean hasCondition() { return conditionObjective != null && !conditionObjective.isEmpty(); }
    public void setCondition(String objective, int score, String target) {
        this.conditionObjective = objective;
        this.conditionScore = score;
        this.conditionTarget = (target == null || target.isEmpty()) ? "*" : target;
    }
    public void clearCondition() {
        this.conditionObjective = null;
        this.conditionScore = 0;
        this.conditionTarget = "*";
    }
    public long getRepeatCooldownTicks() { return repeatCooldownTicks; }
    public void setRepeatCooldownTicks(long ticks) { this.repeatCooldownTicks = Math.max(0, ticks); }
    public long getSequenceCooldownTicks() { return sequenceCooldownTicks; }
    public void setSequenceCooldownTicks(long ticks) { this.sequenceCooldownTicks = Math.max(0, ticks); }
    public String getConditionExpression() { return conditionExpression; }
    public void setConditionExpression(String expr) { this.conditionExpression = (expr == null || expr.isEmpty()) ? null : expr; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String type) { this.triggerType = (type == null || type.isEmpty()) ? null : type; }

    /** Live list, sorted by atSeconds ascending. Server thread only. */
    public List<CommandEvent> getCommandEvents() { return commandEvents; }

    /** Live list, insertion order. Server thread only. */
    public List<String> getFinishCommands() { return finishCommands; }

    public boolean hasScheduledCommands() {
        return !commandEvents.isEmpty() || !finishCommands.isEmpty();
    }

    public int scheduledEntryCount() {
        int count = finishCommands.size();
        for (CommandEvent event : commandEvents) count += event.getCommands().size();
        return count;
    }

    /**
     * Adds a command fired when the displayed time crosses {@code atSeconds}
     * (remaining time for countdown, elapsed time for count-up). Commands at
     * the same instant run in insertion order. Returns false when a sanity
     * cap is hit ({@link #MAX_SCHEDULED_ENTRIES} / {@link #MAX_COMMANDS_PER_POINT}).
     */
    public boolean addScheduledCommand(long atSeconds, String command) {
        if (scheduledEntryCount() >= MAX_SCHEDULED_ENTRIES) return false;
        for (CommandEvent event : commandEvents) {
            if (event.getAtSeconds() == atSeconds) {
                if (event.getCommands().size() >= MAX_COMMANDS_PER_POINT) return false;
                event.getCommands().add(command);
                return true;
            }
        }
        CommandEvent event = new CommandEvent(atSeconds);
        event.getCommands().add(command);
        int pos = 0;
        while (pos < commandEvents.size() && commandEvents.get(pos).getAtSeconds() < atSeconds) pos++;
        commandEvents.add(pos, event);
        return true;
    }

    public boolean addFinishCommand(String command) {
        if (scheduledEntryCount() >= MAX_SCHEDULED_ENTRIES) return false;
        if (finishCommands.size() >= MAX_COMMANDS_PER_POINT) return false;
        finishCommands.add(command);
        return true;
    }

    /** Flattened enumeration shown by list and addressed by remove (see {@link ScheduledEntry}). */
    public List<ScheduledEntry> scheduledEntries() {
        List<ScheduledEntry> entries = new ArrayList<>();
        for (CommandEvent event : commandEvents) {
            for (String c : event.getCommands()) entries.add(new ScheduledEntry(event.getAtSeconds(), c));
        }
        for (String c : finishCommands) entries.add(new ScheduledEntry(null, c));
        return Collections.unmodifiableList(entries);
    }

    /** Removes the entry at the given 0-based index of {@link #scheduledEntries()}. */
    public boolean removeScheduledEntry(int index) {
        if (index < 0) return false;
        for (CommandEvent event : commandEvents) {
            if (index < event.getCommands().size()) {
                event.getCommands().remove(index);
                if (event.getCommands().isEmpty()) commandEvents.remove(event);
                return true;
            }
            index -= event.getCommands().size();
        }
        if (index < finishCommands.size()) {
            finishCommands.remove(index);
            return true;
        }
        return false;
    }

    public void clearScheduledCommands() {
        commandEvents.clear();
        finishCommands.clear();
    }

    public String getConditionExpressionAction() { return conditionExpressionAction; }
    public void setConditionExpressionAction(String a) { this.conditionExpressionAction = "start".equals(a) ? "start" : "finish"; }
    public String getScoreConditionAction() { return scoreConditionAction; }
    public void setScoreConditionAction(String a) { this.scoreConditionAction = "start".equals(a) ? "start" : "finish"; }
    public String getTriggerAction() { return triggerAction; }
    public void setTriggerAction(String a) { this.triggerAction = "start".equals(a) ? "start" : "finish"; }

}