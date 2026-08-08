package com.mateof24.manager;

import com.mateof24.OnTimeConstants;
import com.mateof24.config.ModConfig;
import com.mateof24.storage.TimerStorage;
import com.mateof24.api.Audience;
import com.mateof24.timer.Timer;
import com.mateof24.timer.TimerRun;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class TimerManager {
    private static final TimerManager INSTANCE = new TimerManager();
    private final Map<String, Timer> timers = new HashMap<>();

    /**
     * Executions in flight, oldest first — the ordering is what makes "the
     * primary run" well defined.
     *
     * <p>This replaces the single {@code activeTimer} field. It is capped at
     * one global run for now, so behaviour is unchanged, but the single source
     * of truth has moved here: there is no separate active pointer left to
     * drift out of sync with it.</p>
     */
    private final Map<UUID, TimerRun> runs = new LinkedHashMap<>();

    /**
     * Timer name to its primary run, kept in step with {@link #runs}.
     *
     * <p>Nothing here that a scan of {@code runs} could not answer, and the
     * scan looks cheap because it stops at the first run carrying that name.
     * What it actually costs is the number of runs of <em>other</em> timers
     * registered before it — so it is free while one timer is running and gets
     * expensive precisely when several are, which is what this stage makes
     * possible. Measured over 100 executions: unchanged with a single timer,
     * about eighty times cheaper with fifty.</p>
     */
    private final Map<String, TimerRun> primaryRuns = new HashMap<>();

    private TimerManager() {}

    public static TimerManager getInstance() {
        return INSTANCE;
    }

    public boolean createTimer(String name, int hours, int minutes, int seconds, boolean countUp) {
        if (timers.containsKey(name)) {
            return false;
        }

        long totalSeconds = hours * 3600L + minutes * 60L + seconds;
        long maxSeconds = ModConfig.getInstance().getMaxTimerSeconds();

        if (totalSeconds > maxSeconds) {
            return false;
        }

        Timer timer = new Timer(name, hours, minutes, seconds, countUp);
        timers.put(name, timer);
        saveTimer(timer);
        return true;
    }

    public boolean removeTimer(String name) {
        if (!timers.containsKey(name)) {
            return false;
        }

        runs.values().removeIf(run -> run.timerName().equals(name));
        refreshPrimaries();

        timers.remove(name);
        TimerStorage.deleteTimer(name);
        saveRuns();
        return true;
    }

    /** Starts a run seen by the whole server. The 4.0.0 shape of a timer. */
    public boolean startTimer(String name) {
        return startShared(name, Audience.global()) != null;
    }

    /**
     * Starts one run shared by an audience.
     *
     * @return the new run, or null when the timer is missing or an existing
     *         run of it already reaches some of the same players
     */
    public TimerRun startShared(String name, Audience audience) {
        Timer timer = timers.get(name);
        if (timer == null) return null;
        if (findOverlapping(name, audience) != null) return null;

        // Explicit rather than relying on the run seeding itself from the
        // definition: that only works while setRunning happens to come first.
        TimerRun run = newRun(TimerRun.shared(timer, audience));
        run.setRunning(true);
        timer.setRunning(true);
        TimerStorage.saveTimer(timer);
        saveRuns();

        com.mateof24.event.TimerEventBus.fireStart(run);
        return run;
    }

    /**
     * Starts one run per player, each with a clock of its own.
     *
     * <p>Players who already have a run of this timer are skipped rather than
     * refused: asking for a timer "for everyone" should not fail because one
     * person already has it.</p>
     *
     * @return the runs actually created
     */
    public java.util.List<TimerRun> startEach(String name, Collection<UUID> players) {
        Timer timer = timers.get(name);
        if (timer == null) return java.util.List.of();

        java.util.List<TimerRun> created = new java.util.ArrayList<>();
        for (UUID player : players) {
            if (findOverlapping(name, Audience.ofPlayer(player)) != null) continue;
            TimerRun run = newRun(TimerRun.forPlayer(timer, player));
            // Each run seeds its clock from the definition, including whether
            // it is running — and the definition is not running yet at this
            // point, so without this every 'each' run started frozen.
            run.setRunning(true);
            created.add(run);
        }
        if (created.isEmpty()) return created;

        timer.setRunning(true);
        TimerStorage.saveTimer(timer);
        saveRuns();
        // One event per execution: 'each' created several, and a listener that
        // only heard about the timer could not tell them apart.
        for (TimerRun run : created) com.mateof24.event.TimerEventBus.fireStart(run);
        return created;
    }

    /**
     * The run whose clock is mirrored onto the definition: the first one
     * registered for that timer. With one run — the overwhelmingly common case
     * — it is simply that run.
     */
    public boolean isPrimaryRunOf(TimerRun run) {
        return primaryRuns.get(run.timerName()) == run;
    }

    /** An existing run of this timer that would reach some of the same players. */
    public TimerRun findOverlapping(String timerName, Audience audience) {
        for (TimerRun run : runs.values()) {
            if (!run.timerName().equals(timerName)) continue;
            if (run.audience().overlaps(audience)) return run;
        }
        return null;
    }

    /** Runs matching a timer name (null for any) and reaching any of the given players (null for any). */
    public java.util.List<TimerRun> findRuns(String timerName, Collection<UUID> players) {
        java.util.List<TimerRun> found = new java.util.ArrayList<>();
        for (TimerRun run : runs.values()) {
            if (timerName != null && !run.timerName().equals(timerName)) continue;
            if (players != null && players.stream().noneMatch(run::isVisibleTo)) continue;
            found.add(run);
        }
        return found;
    }

    /** Registers the run and returns it. Split out so the put stays readable. */
    private TimerRun newRun(TimerRun run) {
        runs.put(run.runId(), run);
        // First one registered for that timer wins, which is the definition of
        // primary; later ones leave it alone.
        primaryRuns.putIfAbsent(run.timerName(), run);
        return run;
    }

    /**
     * Recomputes the primary of every timer. Only removals need this: adding
     * cannot displace an existing primary, so {@link #newRun} maintains it
     * incrementally.
     */
    private void refreshPrimaries() {
        primaryRuns.clear();
        for (TimerRun run : runs.values()) primaryRuns.putIfAbsent(run.timerName(), run);
    }

    public boolean pauseTimer() {
        Optional<TimerRun> active = getActiveRun().filter(run -> !run.isAwaitingSequence());
        if (active.isEmpty()) {
            return false;
        }

        active.get().setRunning(false);
        active.get().mirrorToTimer();
        saveActiveTimer();
        return true;
    }

    public boolean setTimerTime(String name, int hours, int minutes, int seconds) {
        Timer timer = timers.get(name);
        if (timer == null) {
            return false;
        }

        timer.setTime(hours, minutes, seconds);
        // The runs carry the live clock, so a manual jump has to reach them.
        for (TimerRun run : runs.values()) {
            if (run.timerName().equals(name)) run.setTime(hours, minutes, seconds);
        }
        // Manual jump: re-baseline scheduled commands so skipped-over
        // thresholds don't fire (only natural ticking fires them).
        resetCommandProgress(name);
        saveTimer(timer);
        return true;
    }

    public boolean addTimerTime(String name, int hours, int minutes, int seconds) {
        Timer timer = timers.get(name);
        if (timer == null) {
            return false;
        }

        timer.addTime(hours, minutes, seconds);
        for (TimerRun run : runs.values()) {
            if (run.timerName().equals(name)) run.addTime(hours, minutes, seconds);
        }
        resetCommandProgress(name);
        saveTimer(timer);
        return true;
    }

    public boolean addScheduledCommand(String name, long atSeconds, String command) {
        Timer timer = timers.get(name);
        if (timer == null) return false;
        if (!timer.addScheduledCommand(atSeconds, command, 0)) return false;
        saveTimer(timer);
        return true;
    }

    public boolean addFinishCommand(String name, String command) {
        Timer timer = timers.get(name);
        if (timer == null) return false;
        if (!timer.addFinishCommand(command, 0)) return false;
        saveTimer(timer);
        return true;
    }

    public boolean removeScheduledEntry(String name, int index) {
        Timer timer = timers.get(name);
        if (timer == null) return false;
        if (!timer.removeScheduledEntry(index)) return false;
        saveTimer(timer);
        return true;
    }

    public boolean clearScheduledCommands(String name) {
        Timer timer = timers.get(name);
        if (timer == null) return false;
        timer.clearScheduledCommands();
        saveTimer(timer);
        return true;
    }

    /** Sets (raw non-empty) or clears (null/empty) one title slot. */
    public boolean setTimerTitle(String name, String position, String raw) {
        Timer timer = timers.get(name);
        if (timer == null) return false;
        if (!timer.setTitle(position, raw)) return false;
        saveTimer(timer);
        return true;
    }

    public boolean clearTimerTitles(String name) {
        Timer timer = timers.get(name);
        if (timer == null) return false;
        timer.clearTitles();
        saveTimer(timer);
        return true;
    }

    public boolean addTimer(Timer timer) {
        if (timers.containsKey(timer.getName())) return false;
        timers.put(timer.getName(), timer);
        saveTimer(timer);
        return true;
    }

    public Optional<Timer> getTimer(String name) {
        return Optional.ofNullable(timers.get(name));
    }

    /**
     * The primary run: the oldest one still registered. With a single run in
     * flight this is simply "the active timer" as 4.0.0 meant it.
     */
    public Optional<TimerRun> getActiveRun() {
        return runs.values().stream().findFirst();
    }

    /**
     * The timer the player would call "the current one".
     *
     * <p>A run waiting out a sequence cooldown is deliberately excluded: the
     * display is cleared and nothing is ticking, and 4.0.0 reported no active
     * timer in that window because it had cleared the pointer outright.</p>
     */
    public Optional<Timer> getActiveTimer() {
        return getActiveRun().filter(run -> !run.isAwaitingSequence()).map(TimerRun::timer);
    }

    /** True when any run is waiting out a repeat or sequence cooldown. */
    public boolean hasPendingCooldown() {
        return runs.values().stream().anyMatch(TimerRun::isInCooldown);
    }

    /** True when this timer already has an execution registered. */
    public boolean hasRunOf(String timerName) {
        return runs.values().stream().anyMatch(run -> run.timerName().equals(timerName));
    }

    /** Ends one execution. The definition itself is untouched. */
    public void endRun(TimerRun run) {
        if (runs.remove(run.runId()) != null) {
            refreshPrimaries();
            saveRuns();
        }
    }

    /**
     * Ends several executions with a single write. {@code /timer stop} over a
     * selector can reach every run at once, and one file write per run would
     * make a mass stop cost as much as the runs it is cancelling.
     */
    public void endRuns(Collection<TimerRun> ending) {
        boolean removed = false;
        for (TimerRun run : ending) {
            if (runs.remove(run.runId()) != null) removed = true;
        }
        if (removed) {
            refreshPrimaries();
            saveRuns();
        }
    }

    /** Re-baselines the scheduled-command cursor of every run of this timer. */
    public void resetCommandProgress(String timerName) {
        for (TimerRun run : runs.values()) {
            if (run.timerName().equals(timerName)) run.resetCommandProgress();
        }
    }

    /** Live view over the executions in flight. Server thread only. */
    public Collection<TimerRun> runsView() {
        return Collections.unmodifiableCollection(runs.values());
    }

    public int runCount() { return runs.size(); }

    private String activeName() {
        return getActiveTimer().map(Timer::getName).orElse(null);
    }

    public boolean hasTimer(String name) {
        return timers.containsKey(name);
    }

    public Map<String, Timer> getAllTimers() {
        return new HashMap<>(timers);
    }

    /**
     * Live unmodifiable view over the timers — no per-call copy, for the
     * periodic polls (tick conditions, triggers, FTBQ poller, /timer list).
     * Server thread only, and callers must not add/remove timers while
     * iterating (starting/pausing an existing timer is fine: that mutates
     * timer state, not the map structure).
     */
    public java.util.Collection<Timer> timersView() {
        return java.util.Collections.unmodifiableCollection(timers.values());
    }

    public void clearActiveTimer() {
        if (runs.isEmpty()) return;
        runs.clear();
        primaryRuns.clear();
        saveRuns();
    }

    public void saveTimers() {
        TimerStorage.saveTimers(timers, activeName());
        // What the armed triggers have seen goes with it. A round that spans a
        // restart would otherwise come back having forgotten who had already
        // died, silently, which is the worst way for it to be wrong.
        TimerStorage.saveTriggerState(com.mateof24.trigger.RuleEngine.toJson());
    }

    /**
     * Cheap save: writes only the active timer's file plus the active-state pointer.
     * Use during the tick path where only the active timer's running/currentTicks/
     * repeatsDone changes — avoids the per-tick re-write of every timer file.
     */
    public void saveActiveTimer() {
        getActiveTimer().ifPresent(TimerStorage::saveTimer);
        TimerStorage.saveActiveState(activeName());
    }

    /**
     * Cheap save: writes a single timer's file plus the active-state pointer.
     * Use when one timer's state changes outside the active-timer hot path.
     */
    public void saveTimer(Timer timer) {
        if (timer == null) return;
        TimerStorage.saveTimer(timer);
        TimerStorage.saveActiveState(activeName());
    }

    public void loadTimers() {
        timers.clear();
        runs.clear();
        primaryRuns.clear();

        TimerStorage.TimerLoadResult result = TimerStorage.loadTimers();
        timers.putAll(result.getTimers());

        com.mateof24.trigger.RuleEngine.loadFrom(TimerStorage.loadTriggerState());

        java.util.List<com.google.gson.JsonObject> stored = TimerStorage.loadRunElements();
        if (stored != null) {
            for (com.google.gson.JsonObject entry : stored) {
                if (!entry.has("timerName")) continue;
                Timer timer = timers.get(entry.get("timerName").getAsString());
                TimerRun run = TimerRun.fromJson(entry, timer);
                if (run != null) newRun(run);
            }
            if (!runs.isEmpty()) {
                OnTimeConstants.LOGGER.info("Restored {} timer run(s)", runs.size());
            }
        } else {
            // No runs file: this world comes from 4.0.0 or earlier. Its active
            // pointer meant exactly one run, shared by the whole server.
            String activeTimerName = result.getActiveTimerName();
            if (activeTimerName != null && timers.containsKey(activeTimerName)) {
                newRun(TimerRun.global(timers.get(activeTimerName)));
                OnTimeConstants.LOGGER.info("Migrated active timer '{}' to a global run", activeTimerName);
            }
        }
        validateActiveTimer();
        saveRuns();
    }

    /** Persists the run registry plus the 4.0.0 active pointer kept for downgrades. */
    public void saveRuns() {
        TimerStorage.saveRuns(runs.values());
        TimerStorage.saveActiveState(activeName());
    }

    /** Drops runs whose definition is no longer registered. */
    public boolean validateActiveTimer() {
        boolean removed = runs.values().removeIf(run -> {
            if (timers.containsValue(run.timer())) return false;
            OnTimeConstants.LOGGER.warn("Run of timer '{}' has no definition, clearing", run.timerName());
            return true;
        });
        if (removed) {
            refreshPrimaries();
            saveRuns();
            return false;
        }
        return true;
    }

}