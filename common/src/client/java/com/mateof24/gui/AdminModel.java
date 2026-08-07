package com.mateof24.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The panel's state: the server's snapshot read into plain rows, plus what the
 * operator has selected.
 *
 * <p>No Minecraft types anywhere in here, which is the point — this is the half
 * of the screen that can be written once and read without knowing anything
 * about widgets. What is left for the per-version files is drawing and input,
 * and nothing else.</p>
 */
public final class AdminModel {

    /** The three jobs the panel is for, which is why there are three tabs. */
    public enum Tab { RUNS, TIMERS, SETTINGS }

    public record RunRow(
            String runId,
            String timerName,
            long currentTicks,
            long targetTicks,
            boolean countUp,
            boolean running,
            String mode,
            String phase,
            String ownerName,
            boolean audienceGlobal,
            List<String> audienceNames,
            int colorHigh,
            int colorMid,
            int colorLow,
            int thresholdMid,
            int thresholdLow,
            long cooldownRemaining,
            String pendingTimer
    ) {
        public boolean inCooldown() { return !"ACTIVE".equals(phase); }

        public boolean each() { return "EACH".equals(mode); }

        /** How many players it reaches, or -1 for a global audience. */
        public int audienceSize() { return audienceGlobal ? -1 : audienceNames.size(); }
    }

    public record TimerRow(
            String name,
            long targetTicks,
            boolean countUp,
            boolean silent,
            String resolvedPreset,
            Float scale,
            int runCount,
            boolean repeat,
            String nextTimer,
            boolean hasTitles,
            List<Scheduled> scheduled,
            List<String> finishCommands,
            JsonObject display,
            /**
             * The server's whole object for this timer.
             *
             * <p>The editor reads twenty-odd more fields than the list does,
             * and naming every one of them here would be twenty more record
             * components that only one screen ever looks at. The named ones
             * above stay named because the list draws them on every frame.</p>
             */
            JsonObject raw
    ) {

        public boolean hasCommands() { return !scheduled.isEmpty() || !finishCommands.isEmpty(); }

        // ---- what only the editor reads ----

        public String title(String slot) {
            if (raw == null || !raw.has("titles") || !raw.get("titles").isJsonObject()) return "";
            return str(raw.getAsJsonObject("titles"), slot, "");
        }

        public int repeatCount() { return (int) numOr(raw, "repeatCount", -1); }

        public long repeatCooldownTicks() { return num(raw, "repeatCooldownTicks"); }

        public long sequenceCooldownTicks() { return num(raw, "sequenceCooldownTicks"); }

        /** One reason this timer starts or ends, as the server describes it. */
    public record Trigger(String kind, String action, String value, int threshold,
                          String scope, String subject, String quantifier, int count) {
        public boolean startsIt() { return "start".equals(action); }
    }

    /** Every trigger of this timer, in the order the server keeps them. */
    public List<Trigger> triggers() {
        List<Trigger> out = new ArrayList<>();
        if (raw == null || !raw.has("triggers") || !raw.get("triggers").isJsonArray()) return out;
        for (JsonElement element : raw.getAsJsonArray("triggers")) {
            if (!element.isJsonObject()) continue;
            JsonObject json = element.getAsJsonObject();
            JsonObject who = json.has("who") && json.get("who").isJsonObject()
                    ? json.getAsJsonObject("who") : new JsonObject();
            out.add(new Trigger(
                    str(json, "kind", ""),
                    str(json, "action", "finish"),
                    str(json, "value", ""),
                    (int) num(json, "threshold"),
                    str(who, "scope", "audience"),
                    str(who, "value", ""),
                    str(who, "quantifier", "any"),
                    (int) numOr(who, "count", 1)));
        }
        return out;
    }

    public String conditionObjective() { return str(raw, "conditionObjective", ""); }

        public int conditionScore() { return (int) num(raw, "conditionScore"); }

        public String conditionTarget() { return str(raw, "conditionTarget", "*"); }

        public String scoreAction() { return str(raw, "conditionAction", "finish"); }

        public String conditionExpression() { return str(raw, "conditionExpression", ""); }

        public String expressionAction() { return str(raw, "conditionExpressionAction", "finish"); }

        public String triggerType() { return str(raw, "triggerType", ""); }

        public String triggerAction() { return str(raw, "triggerAction", "finish"); }

        /**
         * Every command this timer runs, numbered as the server numbers them.
         *
         * <p>{@code atSeconds} is null for a finish command. The numbering is
         * the server's own, so removing row three removes what row three said.
         * </p>
         */
        public List<Scheduled> commandList() {
            List<Scheduled> out = new ArrayList<>();
            if (raw == null || !raw.has("commandList") || !raw.get("commandList").isJsonArray()) return out;
            for (JsonElement element : raw.getAsJsonArray("commandList")) {
                JsonObject entry = element.getAsJsonObject();
                Long at = entry.has("at") && !entry.get("at").isJsonNull() ? entry.get("at").getAsLong() : null;
                out.add(new Scheduled(at == null ? -1L : at, List.of(str(entry, "command", ""))));
            }
            return out;
        }

        /** Repeats for ever when no count was given. */
        public boolean repeatsForever() { return repeat && repeatCount() < 0; }
    }

    /** Commands due at a point on the clock, as the panel lists them. */
    public record Scheduled(long atSeconds, List<String> commands) {}

    public record PlayerRow(String uuid, String name, String team) {}

    private Tab tab = Tab.RUNS;
    private String selectedRunId = null;
    private String selectedTimer = null;
    private String filter = "";
    private String message = null;
    private boolean messageIsError = false;

    private List<RunRow> runs = List.of();
    private List<TimerRow> timers = List.of();
    private List<PlayerRow> players = List.of();
    private JsonObject config = new JsonObject();

    /**
     * Reloads from a snapshot, keeping the selection when what was selected is
     * still there.
     *
     * <p>The panel is repainted from the server once a second, so a selection
     * that reset on every snapshot would be unusable — you would lose the row
     * you were about to click.</p>
     */
    public void apply(JsonObject state) {
        if (state == null) return;
        runs = readRuns(state.getAsJsonArray("runs"));
        timers = readTimers(state.getAsJsonArray("timers"));
        players = readPlayers(state.getAsJsonArray("players"));
        config = state.has("config") ? state.getAsJsonObject("config") : new JsonObject();

        if (selectedRunId != null && runs.stream().noneMatch(r -> r.runId().equals(selectedRunId))) {
            selectedRunId = null;
        }
        if (selectedTimer != null && timers.stream().noneMatch(t -> t.name().equals(selectedTimer))) {
            selectedTimer = null;
        }
    }

    // ---- selection and view state ----

    public Tab tab() { return tab; }

    public void setTab(Tab tab) { this.tab = tab; }

    public String selectedRunId() { return selectedRunId; }

    public void selectRun(String runId) {
        selectedRunId = runId != null && runId.equals(selectedRunId) ? null : runId;
    }

    public RunRow selectedRun() {
        if (selectedRunId == null) return null;
        for (RunRow row : runs) if (row.runId().equals(selectedRunId)) return row;
        return null;
    }

    public String selectedTimer() { return selectedTimer; }

    public void selectTimer(String name) {
        selectedTimer = name != null && name.equals(selectedTimer) ? null : name;
    }

    public String filter() { return filter; }

    public void setFilter(String filter) { this.filter = filter == null ? "" : filter; }

    /** Last thing the server said about an action, shown in the panel rather than in chat. */
    public void setMessage(String message, boolean error) {
        this.message = message;
        this.messageIsError = error;
    }

    public String message() { return message; }

    public boolean messageIsError() { return messageIsError; }

    public void clearMessage() { message = null; }

    // ---- data ----

    public List<RunRow> runs() { return runs; }

    public List<TimerRow> timers() { return timers; }

    /**
     * Drops a timer from the list without waiting for the next snapshot.
     *
     * <p>The server is the authority and will say the same thing a moment
     * later, but a row that stays on screen after being deleted reads as a
     * failed delete — and it stayed until something else forced a redraw, so
     * changing tab or leaving and coming back "fixed" it, which is worse than
     * a plain delay because it looks arbitrary.</p>
     */
    public void forgetTimer(String name) {
        if (name == null) return;
        List<TimerRow> kept = new ArrayList<>(timers.size());
        for (TimerRow row : timers) if (!name.equals(row.name())) kept.add(row);
        timers = kept;
        if (name.equals(selectedTimer)) selectedTimer = null;
    }

    /** The definition by name, or null when it has gone. */
    public TimerRow timer(String name) {
        if (name == null) return null;
        for (TimerRow row : timers) if (row.name().equals(name)) return row;
        return null;
    }

    /** The definition a run belongs to, or null when it has gone. */
    public TimerRow timerOf(RunRow run) {
        if (run == null) return null;
        for (TimerRow row : timers) if (row.name().equals(run.timerName())) return row;
        return null;
    }

    /** Definitions matching the search box, or all of them when it is empty. */
    public List<TimerRow> filteredTimers() {
        if (filter.isEmpty()) return timers;
        String needle = filter.toLowerCase(Locale.ROOT);
        List<TimerRow> out = new ArrayList<>();
        for (TimerRow row : timers) {
            if (row.name().toLowerCase(Locale.ROOT).contains(needle)) out.add(row);
        }
        return out;
    }

    public List<PlayerRow> players() { return players; }

    public JsonObject config() { return config; }

    public int configInt(String key, int fallback) {
        return config.has(key) && !config.get(key).isJsonNull() ? config.get(key).getAsInt() : fallback;
    }

    public float configFloat(String key, float fallback) {
        return config.has(key) && !config.get(key).isJsonNull() ? config.get(key).getAsFloat() : fallback;
    }

    public String configString(String key, String fallback) {
        return config.has(key) && !config.get(key).isJsonNull() ? config.get(key).getAsString() : fallback;
    }

    public boolean configBool(String key, boolean fallback) {
        return config.has(key) && !config.get(key).isJsonNull() ? config.get(key).getAsBoolean() : fallback;
    }

    // ---- reading ----

    private static List<RunRow> readRuns(JsonArray array) {
        List<RunRow> out = new ArrayList<>();
        if (array == null) return out;
        for (JsonElement element : array) {
            JsonObject json = element.getAsJsonObject();
            JsonObject display = json.has("display") && json.get("display").isJsonObject()
                    ? json.getAsJsonObject("display") : null;
            List<String> names = new ArrayList<>();
            if (json.has("audience")) {
                for (JsonElement member : json.getAsJsonArray("audience")) {
                    names.add(str(member.getAsJsonObject(), "name", "?"));
                }
            }
            out.add(new RunRow(
                    str(json, "runId", ""),
                    str(json, "timerName", ""),
                    num(json, "currentTicks"),
                    num(json, "targetTicks"),
                    bool(json, "countUp"),
                    bool(json, "running"),
                    str(json, "mode", "SHARED"),
                    str(json, "phase", "ACTIVE"),
                    str(json, "ownerName", null),
                    "GLOBAL".equals(str(json, "audienceScope", "GLOBAL")),
                    List.copyOf(names),
                    // The timer's own colours, which is why they travel with
                    // the run rather than being looked up in the defaults.
                    (int) numOf(display, "colorHigh", 0xFFFFFF),
                    (int) numOf(display, "colorMid", 0xFFFF00),
                    (int) numOf(display, "colorLow", 0xFF0000),
                    (int) numOf(display, "thresholdMid", 30),
                    (int) numOf(display, "thresholdLow", 10),
                    num(json, "cooldownRemaining"),
                    str(json, "pendingTimer", null)));
        }
        return out;
    }

    private static List<TimerRow> readTimers(JsonArray array) {
        List<TimerRow> out = new ArrayList<>();
        if (array == null) return out;
        for (JsonElement element : array) {
            JsonObject json = element.getAsJsonObject();
            boolean hasTitles = json.has("titles") && json.getAsJsonObject("titles").size() > 0;

            List<Scheduled> scheduled = new ArrayList<>();
            if (json.has("scheduled") && json.get("scheduled").isJsonArray()) {
                for (JsonElement entry : json.getAsJsonArray("scheduled")) {
                    JsonObject at = entry.getAsJsonObject();
                    scheduled.add(new Scheduled(num(at, "at"), strings(at, "commands")));
                }
            }
            List<String> finish = new ArrayList<>(strings(json, "finishCommands"));
            out.add(new TimerRow(
                    str(json, "name", ""),
                    num(json, "targetTicks"),
                    bool(json, "countUp"),
                    bool(json, "silent"),
                    str(json, "resolvedPreset", "BOSSBAR"),
                    json.has("scale") && !json.get("scale").isJsonNull() ? json.get("scale").getAsFloat() : null,
                    (int) num(json, "runCount"),
                    bool(json, "repeat"),
                    str(json, "nextTimer", null),
                    hasTitles,
                    List.copyOf(scheduled),
                    List.copyOf(finish),
                    json.has("display") && json.get("display").isJsonObject()
                            ? json.getAsJsonObject("display") : new JsonObject(),
                    json));
        }
        return out;
    }

    private static List<PlayerRow> readPlayers(JsonArray array) {
        List<PlayerRow> out = new ArrayList<>();
        if (array == null) return out;
        for (JsonElement element : array) {
            JsonObject json = element.getAsJsonObject();
            out.add(new PlayerRow(str(json, "uuid", ""), str(json, "name", "?"), str(json, "team", null)));
        }
        return out;
    }

    private static String str(JsonObject json, String key, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
    }

    private static long numOf(JsonObject json, String key, long fallback) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) return fallback;
        return json.get(key).getAsLong();
    }

    private static List<String> strings(JsonObject json, String key) {
        List<String> out = new ArrayList<>();
        if (json.has(key) && json.get(key).isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray(key)) out.add(element.getAsString());
        }
        return out;
    }

    private static long numOr(JsonObject json, String key, long fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsLong() : fallback;
    }

    private static long num(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsLong() : 0L;
    }

    private static boolean bool(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() && json.get(key).getAsBoolean();
    }
}
