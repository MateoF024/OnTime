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

    private final UUID runId;
    private final String timerName;
    private final Timer timer;
    private final Mode mode;
    /** Player this run belongs to for {@link Mode#EACH}; null otherwise. */
    private final UUID owner;
    private Audience audience;

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

    @Override
    public String toString() {
        return "TimerRun[" + shortId() + " " + timerName + " " + mode + " " + audience + "]";
    }
}
