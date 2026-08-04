package com.mateof24.manager;

import com.mateof24.OnTimeConstants;
import com.mateof24.config.ModConfig;
import com.mateof24.storage.TimerStorage;
import com.mateof24.timer.Audience;
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

        timers.remove(name);
        TimerStorage.deleteTimer(name);
        TimerStorage.saveActiveState(activeName());
        return true;
    }

    public boolean startTimer(String name) {
        Timer timer = timers.get(name);
        if (timer == null) {
            return false;
        }

        // One run at a time for now: starting anything stops whatever was
        // running, which is exactly what the single active timer did.
        Timer previous = getActiveTimer().orElse(null);
        if (previous != null) {
            previous.setRunning(false);
        }
        runs.clear();

        timer.setRunning(true);
        newRun(TimerRun.global(timer));

        if (previous != null && previous != timer) {
            TimerStorage.saveTimer(previous);
        }
        saveActiveTimer();

        getTimer(name).ifPresent(t ->
                com.mateof24.event.TimerEventBus.fireOnStart(toInfo(t)));
        return true;
    }

    /** Registers the run and returns it. Split out so the put stays readable. */
    private TimerRun newRun(TimerRun run) {
        runs.put(run.runId(), run);
        return run;
    }

    public boolean pauseTimer() {
        Optional<Timer> active = getActiveTimer();
        if (active.isEmpty()) {
            return false;
        }

        active.get().setRunning(false);
        saveActiveTimer();
        return true;
    }

    public boolean setTimerTime(String name, int hours, int minutes, int seconds) {
        Timer timer = timers.get(name);
        if (timer == null) {
            return false;
        }

        timer.setTime(hours, minutes, seconds);
        // Manual jump: re-baseline scheduled commands so skipped-over
        // thresholds don't fire (only natural ticking fires them).
        com.mateof24.tick.TimerTickHandler.resetCommandProgress();
        saveTimer(timer);
        return true;
    }

    public boolean addTimerTime(String name, int hours, int minutes, int seconds) {
        Timer timer = timers.get(name);
        if (timer == null) {
            return false;
        }

        timer.addTime(hours, minutes, seconds);
        com.mateof24.tick.TimerTickHandler.resetCommandProgress();
        saveTimer(timer);
        return true;
    }

    public boolean setTimerCommand(String name, String command) {
        Timer timer = timers.get(name);
        if (timer == null) return false;
        timer.setCommand(command);
        saveTimer(timer);
        return true;
    }

    public boolean addScheduledCommand(String name, long atSeconds, String command) {
        Timer timer = timers.get(name);
        if (timer == null) return false;
        if (!timer.addScheduledCommand(atSeconds, command)) return false;
        saveTimer(timer);
        return true;
    }

    public boolean addFinishCommand(String name, String command) {
        Timer timer = timers.get(name);
        if (timer == null) return false;
        if (!timer.addFinishCommand(command)) return false;
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

    public Optional<Timer> getActiveTimer() {
        return getActiveRun().map(TimerRun::timer);
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
        runs.clear();
    }

    public void saveTimers() {
        TimerStorage.saveTimers(timers, activeName());
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

        TimerStorage.TimerLoadResult result = TimerStorage.loadTimers();
        timers.putAll(result.getTimers());

        String activeTimerName = result.getActiveTimerName();
        if (activeTimerName != null && timers.containsKey(activeTimerName)) {
            // Restored as a global run, which is what the stored pointer meant.
            newRun(TimerRun.global(timers.get(activeTimerName)));
            OnTimeConstants.LOGGER.info("Restored active timer: '{}'", activeTimerName);
        }
        validateActiveTimer();
    }

    /** Drops runs whose definition is no longer registered. */
    public boolean validateActiveTimer() {
        boolean removed = runs.values().removeIf(run -> {
            if (timers.containsValue(run.timer())) return false;
            OnTimeConstants.LOGGER.warn("Run of timer '{}' has no definition, clearing", run.timerName());
            return true;
        });
        if (removed) {
            TimerStorage.saveActiveState(activeName());
            return false;
        }
        return true;
    }

    private com.mateof24.api.TimerInfo toInfo(Timer t) {
        return new com.mateof24.api.TimerInfo(t.getName(), t.getCurrentTicks(), t.getTargetTicks(),
                t.isCountUp(), t.isRunning(), t.isSilent(), t.getCommand(),
                t.isRepeat(), t.getRepeatCount(), t.getRepeatsDone());
    }
}