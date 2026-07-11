package com.mateof24.manager;

import com.mateof24.OnTimeConstants;
import com.mateof24.config.ModConfig;
import com.mateof24.storage.TimerStorage;
import com.mateof24.timer.Timer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TimerManager {
    private static final TimerManager INSTANCE = new TimerManager();
    private final Map<String, Timer> timers = new HashMap<>();
    private Timer activeTimer = null;

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

        Timer timer = timers.get(name);
        if (timer == activeTimer) {
            activeTimer = null;
        }

        timers.remove(name);
        TimerStorage.deleteTimer(name);
        TimerStorage.saveActiveState(activeTimer != null ? activeTimer.getName() : null);
        return true;
    }

    public boolean startTimer(String name) {
        Timer timer = timers.get(name);
        if (timer == null) {
            return false;
        }

        Timer previous = activeTimer;
        if (previous != null) {
            previous.setRunning(false);
        }

        timer.setRunning(true);
        activeTimer = timer;
        if (previous != null && previous != timer) {
            TimerStorage.saveTimer(previous);
        }
        saveActiveTimer();

        getTimer(name).ifPresent(t ->
                com.mateof24.event.TimerEventBus.fireOnStart(toInfo(t)));
        return true;
    }

    public boolean pauseTimer() {
        if (activeTimer == null) {
            return false;
        }

        activeTimer.setRunning(false);
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

    public Optional<Timer> getActiveTimer() {
        return Optional.ofNullable(activeTimer);
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
        activeTimer = null;
    }

    public void saveTimers() {
        String activeTimerName = activeTimer != null ? activeTimer.getName() : null;
        TimerStorage.saveTimers(timers, activeTimerName);
    }

    /**
     * Cheap save: writes only the active timer's file plus the active-state pointer.
     * Use during the tick path where only the active timer's running/currentTicks/
     * repeatsDone changes — avoids the per-tick re-write of every timer file.
     */
    public void saveActiveTimer() {
        if (activeTimer != null) {
            TimerStorage.saveTimer(activeTimer);
        }
        TimerStorage.saveActiveState(activeTimer != null ? activeTimer.getName() : null);
    }

    /**
     * Cheap save: writes a single timer's file plus the active-state pointer.
     * Use when one timer's state changes outside the active-timer hot path.
     */
    public void saveTimer(Timer timer) {
        if (timer == null) return;
        TimerStorage.saveTimer(timer);
        TimerStorage.saveActiveState(activeTimer != null ? activeTimer.getName() : null);
    }

    public void loadTimers() {
        timers.clear();
        activeTimer = null;

        TimerStorage.TimerLoadResult result = TimerStorage.loadTimers();
        timers.putAll(result.getTimers());

        String activeTimerName = result.getActiveTimerName();
        if (activeTimerName != null && timers.containsKey(activeTimerName)) {
            activeTimer = timers.get(activeTimerName);
            OnTimeConstants.LOGGER.info("Restored active timer: '{}'", activeTimerName);
        }
        validateActiveTimer();
    }

    public void reloadCommandsFromDisk() {
        TimerStorage.TimerLoadResult result = TimerStorage.loadTimers();
        Map<String, Timer> diskTimers = result.getTimers();

        for (Map.Entry<String, Timer> entry : diskTimers.entrySet()) {
            String name = entry.getKey();
            Timer diskTimer = entry.getValue();
            Timer memTimer = timers.get(name);

            if (memTimer != null) {
                memTimer.setCommand(diskTimer.getCommand());
                memTimer.setSilent(diskTimer.isSilent());
            }
        }
    }

    public void reloadFromDisk() {
        TimerStorage.TimerLoadResult result = TimerStorage.loadTimers();
        Map<String, Timer> diskTimers = result.getTimers();

        for (Map.Entry<String, Timer> entry : diskTimers.entrySet()) {
            String name = entry.getKey();
            Timer diskTimer = entry.getValue();
            Timer memTimer = timers.get(name);

            if (memTimer != null) {
                boolean wasRunning = memTimer.isRunning();
                timers.put(name, diskTimer);
                diskTimer.setRunning(wasRunning);

                if (activeTimer == memTimer) {
                    activeTimer = diskTimer;
                }
            } else {
                timers.put(name, diskTimer);
            }
        }
    }

    public boolean validateActiveTimer() {
        if (activeTimer != null && !timers.containsValue(activeTimer)) {
            OnTimeConstants.LOGGER.warn("Active timer '{}' not found in timers map, clearing", activeTimer.getName());
            activeTimer = null;
            TimerStorage.saveActiveState(null);
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