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

    // Create new timer
    public boolean createTimer(String name, int hours, int minutes, int seconds, boolean countUp) {
        if (timers.containsKey(name)) {
            return false;
        }

        Timer timer = new Timer(name, hours, minutes, seconds, countUp);
        timers.put(name, timer);
        saveTimers();
        return true;
    }

    // Remove timer
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

    // Start timer
    public boolean startTimer(String name) {
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

    // Pause active timer
    public boolean pauseTimer() {
        if (activeTimer == null) {
            return false;
        }

        activeTimer.setRunning(false);
        saveTimers();
        return true;
    }

    // Set timer time
    public boolean setTimerTime(String name, int hours, int minutes, int seconds) {
        Timer timer = timers.get(name);
        if (timer == null) {
            return false;
        }

        timer.setTime(hours, minutes, seconds);
        saveTimers();
        return true;
    }

    // Add time to timer
    public boolean addTimerTime(String name, int hours, int minutes, int seconds) {
        Timer timer = timers.get(name);
        if (timer == null) {
            return false;
        }

        timer.addTime(hours, minutes, seconds);
        saveTimers();
        return true;
    }

    // Get timer
    public Optional<Timer> getTimer(String name) {
        return Optional.ofNullable(timers.get(name));
    }

    // Get active timer
    public Optional<Timer> getActiveTimer() {
        return Optional.ofNullable(activeTimer);
    }

    // Check if timer exists
    public boolean hasTimer(String name) {
        return timers.containsKey(name);
    }

    // Get all timers
    public Map<String, Timer> getAllTimers() {
        return new HashMap<>(timers);
    }

    // Clear active timer reference
    public void clearActiveTimer() {
        activeTimer = null;
    }

    // Save timers to disk
    public void saveTimers() {
        TimerStorage.saveTimers(timers);
    }

    // Load timers from disk
    public void loadTimers() {
        timers.clear();
        activeTimer = null;

        Map<String, Timer> loadedTimers = TimerStorage.loadTimers();
        timers.putAll(loadedTimers);

        // Find active timer if any
        for (Timer timer : timers.values()) {
            if (timer.isRunning()) {
                activeTimer = timer;
                break;
            }
        }
    }
}