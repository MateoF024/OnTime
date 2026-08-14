package com.mateof24.api;

import com.mateof24.command.PlaceholderSystem;
import com.mateof24.manager.TimerManager;
import com.mateof24.timer.Timer;
import com.mateof24.timer.TimerRun;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The entry point for other mods.
 *
 * <p><b>Definitions and executions are different things.</b> A
 * {@link TimerDefinition} is what an operator configured; a {@link TimerRunInfo}
 * is one running instance of it, with its own clock and its own audience. One
 * definition can have none, one, or one execution per player. Every operation
 * below belongs to one side or the other, and the ones that act on an execution
 * take its {@code runId}.</p>
 *
 * <p><b>Threading.</b> Everything here reads or writes server state and must be
 * called on the server thread. From another thread, hand it to
 * {@code server.execute(...)} first.</p>
 *
 * <p><b>Compatibility.</b> {@link #API_VERSION} is bumped whenever a signature
 * is removed or changes meaning; check it if you support more than one OnTime.
 * Nothing here is deprecated: 5.0.0 is a fresh API rather than the old one
 * with parts crossed out, and what it does differently is announced in the
 * changelog.</p>
 */
public class OnTimeAPI {

    /**
     * 1 = OnTime 4.x. 2 = 5.0.0: executions became first-class, and the
     * operations that assumed a single active timer were removed.
     */
    public static final int API_VERSION = 2;

    public static final String SCOREBOARD_OBJECTIVE = "ontime_active";

    private static final OnTimeAPI INSTANCE = new OnTimeAPI();

    private OnTimeAPI() {}

    public static OnTimeAPI getInstance() { return INSTANCE; }

    // ==================================================================
    // Definitions
    // ==================================================================

    public boolean createTimer(String name, int hours, int minutes, int seconds, boolean countUp) {
        return TimerManager.getInstance().createTimer(name, hours, minutes, seconds, countUp);
    }

    /** Deletes the definition and ends every execution of it. */
    public boolean removeTimer(String name) {
        return TimerManager.getInstance().removeTimer(name);
    }

    public boolean hasTimer(String name) {
        return TimerManager.getInstance().hasTimer(name);
    }

    /** Snapshot of one definition, or empty when there is no timer by that name. */
    public Optional<TimerDefinition> getDefinition(String name) {
        return TimerManager.getInstance().getTimer(name).map(ApiViews::of);
    }

    /** Snapshot of every definition, in no particular order. */
    public List<TimerDefinition> getDefinitions() {
        List<TimerDefinition> out = new ArrayList<>();
        for (Timer timer : TimerManager.getInstance().timersView()) out.add(ApiViews.of(timer));
        return out;
    }

    /**
     * Sets the starting time. Executions already in flight jump to it too —
     * changing what a timer is changes what its clocks are counting.
     */
    public boolean setTimerTime(String name, int hours, int minutes, int seconds) {
        return TimerManager.getInstance().setTimerTime(name, hours, minutes, seconds);
    }

    public boolean addTimerTime(String name, int hours, int minutes, int seconds) {
        return TimerManager.getInstance().addTimerTime(name, hours, minutes, seconds);
    }

    /**
     * Adds a command fired when the timer's displayed time crosses
     * {@code atSeconds} (remaining time for countdown timers, elapsed time
     * for count-up timers). Must satisfy 0 &lt; atSeconds &lt; duration.
     * Commands at the same instant run in the order they were added.
     */
    public boolean addScheduledCommand(String name, long atSeconds, String command) {
        Optional<Timer> timer = TimerManager.getInstance().getTimer(name);
        if (timer.isEmpty()) return false;
        long targetSeconds = timer.get().getTargetTicks() / 20L;
        if (atSeconds <= 0 || atSeconds >= targetSeconds) return false;
        return TimerManager.getInstance().addScheduledCommand(name, atSeconds, command);
    }

    /**
     * Adds a command to the finish sequence, executed after the legacy
     * single finish command ({@link #setTimerCommand}) in insertion order.
     */
    public boolean addFinishCommand(String name, String command) {
        return TimerManager.getInstance().addFinishCommand(name, command);
    }

    /** Removes every scheduled and finish command this timer has. */
    public boolean clearScheduledCommands(String name) {
        return TimerManager.getInstance().clearScheduledCommands(name);
    }

    /**
     * Sets (rawText non-empty) or clears (null/empty) one of the four
     * decorative titles rendered around the timer. Position is one of
     * "above"/"below"/"left"/"right". Accepts plain text or a tellraw-style
     * JSON component; returns false for an unknown position, invalid JSON or
     * a missing timer.
     */
    public boolean setTimerTitle(String name, String position, String rawText) {
        if (rawText != null && !rawText.isEmpty()
                && com.mateof24.compat.VanillaCompat.parseTitle(rawText) == null) {
            return false;
        }
        return TimerManager.getInstance().setTimerTitle(name, position, rawText);
    }

    public boolean clearTimerTitles(String name) {
        return TimerManager.getInstance().clearTimerTitles(name);
    }

    public boolean setTimerRepeat(String name, boolean repeat, int count) {
        return TimerManager.getInstance().getTimer(name).map(t -> {
            t.setRepeat(repeat);
            t.setRepeatCount(count);
            TimerManager.getInstance().saveTimers();
            return true;
        }).orElse(false);
    }

    /**
     * Where this timer draws. {@code preset} is a
     * {@code TimerPositionPreset} name, or null to inherit the server default.
     *
     * <p>Refused when an execution of this timer is in flight and the preset is
     * already taken for someone who would then see two timers in one place —
     * every preset but {@code CUSTOM} is a single anchor.</p>
     */
    public boolean setTimerPosition(String name, String preset) {
        Timer timer = TimerManager.getInstance().getTimer(name).orElse(null);
        if (timer == null) return false;

        String resolved = preset != null
                ? preset.toUpperCase(java.util.Locale.ROOT)
                : com.mateof24.config.ModConfig.getInstance().getPositionPreset().name();
        if (preset != null && com.mateof24.config.TimerPositionPreset.parse(preset) == null) return false;

        for (TimerRun run : TimerManager.getInstance().findRuns(name, null)) {
            if (com.mateof24.manager.DisplaySlots.occupant(resolved, run.audience(), name) != null) {
                return false;
            }
        }
        timer.display().setPreset(resolved);
        TimerManager.getInstance().saveTimer(timer);
        com.mateof24.network.TimerState.markDirty();
        return true;
    }

    /** Pins this timer at screen coordinates, which also puts it on {@code CUSTOM}. */
    public boolean setTimerCustomPosition(String name, int x, int y) {
        return TimerManager.getInstance().getTimer(name).map(timer -> {
            timer.display().setPreset(com.mateof24.config.TimerPositionPreset.CUSTOM.name());
            timer.display().setX(x);
            timer.display().setY(y);
            TimerManager.getInstance().saveTimer(timer);
            com.mateof24.network.TimerState.markDirty();
            return true;
        }).orElse(false);
    }

    /**
     * Text scale for this timer.
     *
     * <p>Null means "take the current server default" rather than "inherit
     * it": a timer owns its own scale from the moment it is created, so there
     * is nothing left to inherit from.</p>
     */
    public boolean setTimerScale(String name, Float scale) {
        return TimerManager.getInstance().getTimer(name).map(timer -> {
            timer.display().setScale(scale != null ? scale
                    : com.mateof24.config.ModConfig.getInstance().getTimerScale());
            TimerManager.getInstance().saveTimer(timer);
            com.mateof24.network.TimerState.markDirty();
            return true;
        }).orElse(false);
    }

    // ==================================================================
    // Executions
    // ==================================================================

    /**
     * Starts a run seen by the whole server, including players who connect
     * later. The shape every 4.x timer had.
     *
     * @return the new run's id, or empty when the timer is missing, an
     *         execution of it is already in flight, or its slot is taken
     */
    public Optional<UUID> startGlobal(String name) {
        List<UUID> created = startRun(name, Audience.global(), RunMode.SHARED);
        return created.isEmpty() ? Optional.empty() : Optional.of(created.get(0));
    }

    /**
     * Starts one or more executions.
     *
     * <p>{@link RunMode#SHARED} makes a single execution however many players
     * the audience covers — one clock, several viewers. {@link RunMode#EACH}
     * makes one per player, each with its own clock, and skips players who
     * already have an execution of this timer rather than failing outright.</p>
     *
     * <p>A fixed audience is resolved once, here: a player who connects
     * afterwards is not part of it. Use {@link Audience#global()} for the
     * everyone-including-latecomers meaning.</p>
     *
     * @return the ids created, empty when nothing could start
     */
    public List<UUID> startRun(String name, Audience audience, RunMode mode) {
        TimerManager manager = TimerManager.getInstance();
        Timer timer = manager.getTimer(name).orElse(null);
        if (timer == null || audience == null) return List.of();

        String preset = com.mateof24.manager.DisplaySlots.presetOf(timer);
        if (com.mateof24.manager.DisplaySlots.occupant(preset, audience, name) != null) return List.of();

        if (mode == RunMode.EACH) {
            if (audience.isGlobal()) return List.of();
            List<TimerRun> created = manager.startEach(name, audience.players());
            List<UUID> ids = new ArrayList<>(created.size());
            for (TimerRun run : created) ids.add(run.runId());
            return ids;
        }

        TimerRun run = manager.startShared(name, audience);
        return run == null ? List.of() : List.of(run.runId());
    }

    /** Every execution in flight, oldest first. */
    public List<TimerRunInfo> getRuns() {
        List<TimerRunInfo> out = new ArrayList<>();
        for (TimerRun run : TimerManager.getInstance().runsView()) out.add(ApiViews.of(run));
        return out;
    }

    /** Executions of one timer, oldest first. */
    public List<TimerRunInfo> getRunsOf(String name) {
        List<TimerRunInfo> out = new ArrayList<>();
        for (TimerRun run : TimerManager.getInstance().findRuns(name, null)) out.add(ApiViews.of(run));
        return out;
    }

    /** Executions this player can see, whichever timer they belong to. */
    public List<TimerRunInfo> getRunsFor(UUID player) {
        List<TimerRunInfo> out = new ArrayList<>();
        for (TimerRun run : TimerManager.getInstance().runsView()) {
            if (run.isVisibleTo(player)) out.add(ApiViews.of(run));
        }
        return out;
    }

    public Optional<TimerRunInfo> getRun(UUID runId) {
        return findRun(runId).map(ApiViews::of);
    }

    /**
     * The oldest execution still in flight, of any timer.
     *
     * <p>Provided for the places that genuinely need one answer — a placeholder
     * substituting into a scoreboard line has nowhere to put three. It is a
     * stable but arbitrary pick: with several executions there is no such thing
     * as "the" one, so prefer {@link #getRuns()} wherever you can handle a
     * list.</p>
     */
    public Optional<TimerRunInfo> getPrimaryRun() {
        return TimerManager.getInstance().getActiveRun().map(ApiViews::of);
    }

    /** @return false when there is no such execution, or it was already paused */
    public boolean pauseRun(UUID runId) { return setRunning(runId, false); }

    /** @return false when there is no such execution, or it was already running */
    public boolean resumeRun(UUID runId) { return setRunning(runId, true); }

    /** Ends one execution: its clock goes back to the start and it is deregistered. */
    public boolean stopRun(UUID runId) {
        return findRun(runId).map(run -> {
            run.cancelPending();
            run.resetRepeatsDone();
            run.reset();
            if (TimerManager.getInstance().isPrimaryRunOf(run)) run.mirrorToTimer();
            com.mateof24.trigger.RuleEngine.resetFor(run.timerName());
            com.mateof24.trigger.TriggerProgress.resetFor(run.timerName());
            TimerManager.getInstance().endRun(run);
            TimerManager.getInstance().saveTimers();
            com.mateof24.network.TimerState.markDirty();
            return true;
        }).orElse(false);
    }

    /** Puts one execution back to its starting time. It stays in flight. */
    public boolean resetRun(UUID runId) {
        return findRun(runId).map(run -> {
            run.cancelPending();
            run.reset();
            if (TimerManager.getInstance().isPrimaryRunOf(run)) {
                run.mirrorToTimer();
                run.timer().reset();
            }
            TimerManager.getInstance().saveTimers();
            com.mateof24.network.TimerState.markDirty();
            return true;
        }).orElse(false);
    }

    /** Ends every execution. @return how many there were */
    public int stopAllRuns() {
        List<TimerRun> all = List.copyOf(TimerManager.getInstance().runsView());
        for (TimerRun run : all) stopRun(run.runId());
        return all.size();
    }

    /**
     * Replaces who sees an execution.
     *
     * @return false when there is no such execution, or the new audience would
     *         give someone two executions of the same timer
     */
    public boolean setRunAudience(UUID runId, Audience audience) {
        if (audience == null) return false;
        return findRun(runId).map(run -> {
            TimerRun clash = TimerManager.getInstance().findOverlapping(run.timerName(), audience);
            if (clash != null && clash != run) return false;
            run.setAudience(audience);
            TimerManager.getInstance().saveRuns();
            com.mateof24.network.TimerState.markDirty();
            return true;
        }).orElse(false);
    }

    private Optional<TimerRun> findRun(UUID runId) {
        if (runId == null) return Optional.empty();
        for (TimerRun run : TimerManager.getInstance().runsView()) {
            if (run.runId().equals(runId)) return Optional.of(run);
        }
        return Optional.empty();
    }

    private boolean setRunning(UUID runId, boolean running) {
        return findRun(runId).map(run -> {
            if (run.isInCooldown() || run.isRunning() == running) return false;
            run.setRunning(running);
            if (TimerManager.getInstance().isPrimaryRunOf(run)) run.mirrorToTimer();
            TimerManager.getInstance().saveTimers();
            if (running) com.mateof24.event.TimerEventBus.fireResume(run);
            else com.mateof24.event.TimerEventBus.firePause(run);
            com.mateof24.network.TimerState.markDirty();
            return true;
        }).orElse(false);
    }

    // ==================================================================
    // Events
    // ==================================================================

    public void onRunStart(Consumer<TimerRunInfo> listener) {
        com.mateof24.event.TimerEventBus.registerOnRunStart(listener);
    }

    public void onRunFinish(Consumer<TimerRunInfo> listener) {
        com.mateof24.event.TimerEventBus.registerOnRunFinish(listener);
    }

    public void onRunPause(Consumer<TimerRunInfo> listener) {
        com.mateof24.event.TimerEventBus.registerOnRunPause(listener);
    }

    public void onRunResume(Consumer<TimerRunInfo> listener) {
        com.mateof24.event.TimerEventBus.registerOnRunResume(listener);
    }

    /** Fires once per second per running execution. */
    public void onRunTick(Consumer<TimerRunInfo> listener) {
        com.mateof24.event.TimerEventBus.registerOnRunTick(listener);
    }

    /**
     * Extra finish condition for a timer: while it returns true the timer ends
     * early. Evaluated every tick for every execution of that timer.
     */
    public void registerFinishCondition(String timerName, java.util.function.Supplier<Boolean> condition) {
        com.mateof24.event.TimerConditionRegistry.register(timerName, condition);
    }

    public void unregisterFinishCondition(String timerName) {
        com.mateof24.event.TimerConditionRegistry.unregister(timerName);
    }

    // ==================================================================
    // Server integration
    // ==================================================================

    /** The scoreboard objective the mod publishes clocks to. */
    public String getScoreboardObjectiveName() {
        return SCOREBOARD_OBJECTIVE;
    }

    public void setPermissionProvider(com.mateof24.permission.PermissionHelper.IPermissionProvider provider) {
        com.mateof24.permission.PermissionHelper.setProvider(provider);
    }

    /** Adds a {@code {key}} placeholder resolved against a timer's definition. */
    public void registerTimerPlaceholder(String key, Function<Timer, String> resolver) {
        PlaceholderSystem.registerPlaceholder(key, resolver);
    }

    public void startWebSocket(int port) {
        com.mateof24.websocket.TimerWebSocketServer.getInstance().start(port);
    }

    public void stopWebSocket() {
        com.mateof24.websocket.TimerWebSocketServer.getInstance().stop();
    }

    public boolean isWebSocketRunning() {
        return com.mateof24.websocket.TimerWebSocketServer.getInstance().isRunning();
    }

}
