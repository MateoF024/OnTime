package com.mateof24.timer;

import java.util.UUID;

/**
 * One execution of a {@link Timer}.
 *
 * <p>{@code Timer} used to be both the definition and the execution: its
 * {@code currentTicks} and {@code running} lived next to the finish command and
 * the conditions, which is exactly why two executions of the same timer were
 * impossible. The definition stays in {@code Timer}; everything that is true of
 * a single run — its clock, who watches it, how far along its scheduled
 * commands are — belongs here.</p>
 *
 * <p><b>Staging note.</b> In this phase the clock accessors still delegate to
 * the {@code Timer}, so every existing reader keeps seeing the same values and
 * behaviour is unchanged. The tick state moves in for real when the tick engine
 * does, and this class becomes the authority then.</p>
 */
public final class TimerRun {

    /** How a selector expands into runs. */
    public enum Mode {
        /** One run, one clock, several viewers. */
        SHARED,
        /** One run per matched player, each with its own clock. */
        EACH
    }

    /** What a run is doing between ticks. */
    public enum Phase {
        /** Ticking, or paused — the timer is the current one either way. */
        ACTIVE,
        /** Finished a lap, waiting out the repeat cooldown before the next one. */
        REPEAT_COOLDOWN,
        /** Finished, waiting out the sequence cooldown before the next timer starts. */
        SEQUENCE_COOLDOWN
    }

    private final UUID runId;
    private final String timerName;
    private final Timer timer;
    private final Mode mode;
    /** Player this run belongs to for {@link Mode#EACH}; null otherwise. */
    private final UUID owner;
    private Audience audience;

    // ---- per-run tick state ----
    // All of this used to be static on TimerTickHandler, which is the reason a
    // second execution was impossible: two runs would have shared one cooldown,
    // one scheduled-command cursor and one command queue.

    private Phase phase = Phase.ACTIVE;
    private long cooldownRemaining = 0L;
    /** Timer to start once a sequence cooldown elapses; null when not sequencing. */
    private String pendingSequenceTimer = null;

    /**
     * Last displayed second, so scheduled commands fire exactly once when a
     * threshold is crossed by natural ticking. -1 means "take a fresh baseline
     * on the next tick", which is what a manual time jump wants: skipping over
     * a threshold must not fire it.
     */
    private long lastCommandSecond = -1L;

    /** Last second written to the scoreboard; -1 forces a write on the first tick. */
    private long lastScoreboardSecond = -1L;

    /**
     * Commands waiting on the configured delay, with placeholders already
     * resolved so {time} and {seconds} reflect the instant they were queued.
     */
    private final java.util.ArrayDeque<String> pendingCommands = new java.util.ArrayDeque<>();
    private long commandDelayRemaining = 0L;

    private TimerRun(UUID runId, Timer timer, Audience audience, Mode mode, UUID owner) {
        this.runId = runId;
        this.timer = timer;
        this.timerName = timer.getName();
        this.audience = audience;
        this.mode = mode;
        this.owner = owner;
    }

    /** A run shared by everyone on the server, the shape every 4.0.0 timer had. */
    public static TimerRun global(Timer timer) {
        return new TimerRun(UUID.randomUUID(), timer, Audience.global(), Mode.SHARED, null);
    }

    /** A run watched by a fixed set of players, sharing one clock. */
    public static TimerRun shared(Timer timer, Audience audience) {
        return new TimerRun(UUID.randomUUID(), timer, audience, Mode.SHARED, null);
    }

    /** A run belonging to one player, with a clock of its own. */
    public static TimerRun forPlayer(Timer timer, UUID player) {
        return new TimerRun(UUID.randomUUID(), timer, Audience.ofPlayer(player), Mode.EACH, player);
    }

    public UUID runId() { return runId; }

    /** Short form for logs, messages and the {run} placeholder. */
    public String shortId() { return runId.toString().substring(0, 8); }

    public String timerName() { return timerName; }

    public Timer timer() { return timer; }

    public Mode mode() { return mode; }

    /** Null unless {@link #mode()} is {@link Mode#EACH}. */
    public UUID owner() { return owner; }

    public Audience audience() { return audience; }

    public void setAudience(Audience audience) { this.audience = audience; }

    public boolean isVisibleTo(UUID player) { return audience.includes(player); }

    // ---- clock state (delegated for now, see the staging note above) ----

    public long getCurrentTicks() { return timer.getCurrentTicks(); }

    public void setCurrentTicks(long ticks) { timer.setCurrentTicks(ticks); }

    public long getTargetTicks() { return timer.getTargetTicks(); }

    public boolean isCountUp() { return timer.isCountUp(); }

    public boolean isRunning() { return timer.isRunning(); }

    public void setRunning(boolean running) { timer.setRunning(running); }

    public boolean isSilent() { return timer.isSilent(); }

    public int getRepeatsDone() { return timer.getRepeatsDone(); }

    public void incrementRepeatsDone() { timer.incrementRepeatsDone(); }

    public void resetRepeatsDone() { timer.resetRepeatsDone(); }

    public boolean shouldRepeatAgain() { return timer.shouldRepeatAgain(); }

    public boolean tick() { return timer.tick(); }

    public void reset() { timer.reset(); }

    public String getFormattedTime() { return timer.getFormattedTime(); }

    // ---- phase and cooldowns ----

    public Phase phase() { return phase; }

    /**
     * True while waiting for the next timer of a sequence. Such a run holds no
     * timer the player would call "the current one" — the display is cleared
     * and nothing is ticking — so it is deliberately not reported as active.
     */
    public boolean isAwaitingSequence() { return phase == Phase.SEQUENCE_COOLDOWN; }

    public boolean isInCooldown() { return phase != Phase.ACTIVE; }

    public void beginRepeatCooldown(long ticks) {
        phase = Phase.REPEAT_COOLDOWN;
        cooldownRemaining = ticks;
    }

    public void beginSequenceCooldown(String nextTimer, long ticks) {
        phase = Phase.SEQUENCE_COOLDOWN;
        pendingSequenceTimer = nextTimer;
        cooldownRemaining = ticks;
    }

    /** @return true while the cooldown is still counting down */
    public boolean tickCooldown() {
        if (cooldownRemaining > 0) {
            cooldownRemaining--;
            return true;
        }
        return false;
    }

    public String pendingSequenceTimer() { return pendingSequenceTimer; }

    public void endCooldown() {
        phase = Phase.ACTIVE;
        cooldownRemaining = 0L;
        pendingSequenceTimer = null;
    }

    // ---- scheduled command progress ----

    /**
     * Re-baselines the scheduled-command cursor without firing anything. Used
     * on a manual time jump: crossing a threshold by hand must not fire it.
     */
    public void resetCommandProgress() {
        lastCommandSecond = -1L;
    }

    public long lastCommandSecond() { return lastCommandSecond; }

    public void setLastCommandSecond(long second) { this.lastCommandSecond = second; }

    public long lastScoreboardSecond() { return lastScoreboardSecond; }

    public void setLastScoreboardSecond(long second) { this.lastScoreboardSecond = second; }

    // ---- paced command queue ----

    public void queueCommand(String resolvedCommand) { pendingCommands.add(resolvedCommand); }

    public boolean hasPendingCommands() { return !pendingCommands.isEmpty(); }

    public String pollPendingCommand() { return pendingCommands.poll(); }

    public boolean tickCommandDelay() {
        if (commandDelayRemaining > 0) {
            commandDelayRemaining--;
            return true;
        }
        return false;
    }

    public void setCommandDelay(long ticks) { this.commandDelayRemaining = ticks; }

    public void clearPendingCommands() {
        pendingCommands.clear();
        commandDelayRemaining = 0L;
    }

    /** Full reset of the per-run tick state, for /timer stop and friends. */
    public void cancelPending() {
        endCooldown();
        resetCommandProgress();
        clearPendingCommands();
        lastScoreboardSecond = -1L;
    }

    // ---- persistence ----

    /**
     * Serialises identity and audience.
     *
     * <p>Two things are deliberately absent. The clock, because while it still
     * delegates to {@link Timer} the timer file is its only home and writing it
     * here as well would create a second source of truth. And the cooldowns,
     * because 4.0.0 held them in static fields that a restart simply dropped —
     * restoring one would auto-start a timer at boot that the operator did not
     * ask for, which is worse than losing a cooldown.</p>
     */
    public com.google.gson.JsonObject toJson() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("runId", runId.toString());
        json.addProperty("timerName", timerName);
        json.addProperty("mode", mode.name());
        if (owner != null) json.addProperty("owner", owner.toString());
        json.addProperty("audienceScope", audience.scope().name());
        if (!audience.isGlobal()) {
            com.google.gson.JsonArray players = new com.google.gson.JsonArray();
            for (UUID player : audience.players()) players.add(player.toString());
            json.add("audiencePlayers", players);
        }
        return json;
    }

    /**
     * Rebuilds a run from disk against an already-loaded definition.
     *
     * @return null when the entry is unusable, so one bad record cannot stop
     *         the rest from loading
     */
    public static TimerRun fromJson(com.google.gson.JsonObject json, Timer timer) {
        if (timer == null) return null;
        try {
            UUID runId = json.has("runId")
                    ? UUID.fromString(json.get("runId").getAsString())
                    : UUID.randomUUID();
            Mode mode = json.has("mode")
                    ? Mode.valueOf(json.get("mode").getAsString())
                    : Mode.SHARED;
            UUID owner = json.has("owner") ? UUID.fromString(json.get("owner").getAsString()) : null;

            Audience audience = Audience.global();
            if (json.has("audienceScope")
                    && Audience.Scope.PLAYERS.name().equals(json.get("audienceScope").getAsString())) {
                java.util.List<UUID> players = new java.util.ArrayList<>();
                if (json.has("audiencePlayers") && json.get("audiencePlayers").isJsonArray()) {
                    for (com.google.gson.JsonElement el : json.getAsJsonArray("audiencePlayers")) {
                        try { players.add(UUID.fromString(el.getAsString())); } catch (Exception ignored) {}
                    }
                }
                audience = Audience.ofPlayers(players);
            }

            // Always restored ACTIVE: cooldowns are in-memory by design, see
            // toJson(). Whether the timer resumes ticking is decided by the
            // usual wasRunningBeforeShutdown check, exactly as in 4.0.0.
            return new TimerRun(runId, timer, audience, mode, owner);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "TimerRun[" + shortId() + " " + timerName + " " + mode + " " + audience + "]";
    }
}
