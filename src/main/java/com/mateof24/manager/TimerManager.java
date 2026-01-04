package com.mateof24.manager;

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

        Timer timer = new Timer(name, hours, minutes, seconds, countUp);
        timers.put(name, timer);
        saveTimers();
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
        saveTimers();
        return true;
    }

    public boolean startTimer(String name) {
        reloadCommandsFromDisk();

        Timer timer = timers.get(name);
        if (timer == null) {
            return false;
        }

        if (activeTimer != null) {
            activeTimer.setRunning(false);
        }

        timer.setRunning(true);
        activeTimer = timer;
        saveTimers();
        return true;
    }

    public boolean pauseTimer() {
        if (activeTimer == null) {
            return false;
        }

        activeTimer.setRunning(false);
        saveTimers();
        return true;
    }

    public boolean setTimerTime(String name, int hours, int minutes, int seconds) {
        Timer timer = timers.get(name);
        if (timer == null) {
            return false;
        }

        timer.setTime(hours, minutes, seconds);
        saveTimers();
        return true;
    }

    public boolean addTimerTime(String name, int hours, int minutes, int seconds) {
        Timer timer = timers.get(name);
        if (timer == null) {
            return false;
        }

        timer.addTime(hours, minutes, seconds);
        saveTimers();
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

    public void clearActiveTimer() {
        activeTimer = null;
    }

    public void saveTimers() {
        TimerStorage.saveTimers(timers);
    }

    public void loadTimers() {
        timers.clear();
        activeTimer = null;

        Map<String, Timer> loadedTimers = TimerStorage.loadTimers();
        timers.putAll(loadedTimers);

        for (Timer timer : timers.values()) {
            if (timer.isRunning()) {
                activeTimer = timer;
                break;
            }
        }
    }

    public void reloadCommandsFromDisk() {
        Map<String, Timer> diskTimers = TimerStorage.loadTimers();

        for (Map.Entry<String, Timer> entry : diskTimers.entrySet()) {
            String name = entry.getKey();
            Timer diskTimer = entry.getValue();
            Timer memTimer = timers.get(name);

            if (memTimer != null) {
                memTimer.setCommand(diskTimer.getCommand());
            }
        }
    }
}