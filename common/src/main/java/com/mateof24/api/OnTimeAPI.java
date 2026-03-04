package com.mateof24.api;

import com.mateof24.command.PlaceholderSystem;
import com.mateof24.manager.TimerManager;
import com.mateof24.timer.Timer;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class OnTimeAPI {

    private static final OnTimeAPI INSTANCE = new OnTimeAPI();

    private OnTimeAPI() {}

    public static OnTimeAPI getInstance() { return INSTANCE; }

    public boolean createTimer(String name, int hours, int minutes, int seconds, boolean countUp) {
        return TimerManager.getInstance().createTimer(name, hours, minutes, seconds, countUp);
    }

    public boolean removeTimer(String name) {
        return TimerManager.getInstance().removeTimer(name);
    }

    public boolean startTimer(String name) {
        return TimerManager.getInstance().startTimer(name);
    }

    public boolean stopActiveTimer() {
        return TimerManager.getInstance().getActiveTimer().map(t -> {
            t.reset();
            TimerManager.getInstance().clearActiveTimer();
            TimerManager.getInstance().saveTimers();
            return true;
        }).orElse(false);
    }

    public boolean pauseActiveTimer() {
        return TimerManager.getInstance().pauseTimer();
    }

    public boolean setTimerTime(String name, int hours, int minutes, int seconds) {
        return TimerManager.getInstance().setTimerTime(name, hours, minutes, seconds);
    }

    public boolean addTimerTime(String name, int hours, int minutes, int seconds) {
        return TimerManager.getInstance().addTimerTime(name, hours, minutes, seconds);
    }

    public Optional<TimerInfo> getTimer(String name) {
        return TimerManager.getInstance().getTimer(name).map(this::toInfo);
    }

    public Optional<TimerInfo> getActiveTimer() {
        return TimerManager.getInstance().getActiveTimer().map(this::toInfo);
    }

    public Map<String, TimerInfo> getAllTimers() {
        return TimerManager.getInstance().getAllTimers().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> toInfo(e.getValue())));
    }

    public boolean hasTimer(String name) {
        return TimerManager.getInstance().hasTimer(name);
    }

    public void registerTimerPlaceholder(String key, Function<Timer, String> resolver) {
        PlaceholderSystem.registerPlaceholder(key, resolver);
    }

    private TimerInfo toInfo(Timer t) {
        return new TimerInfo(t.getName(), t.getCurrentTicks(), t.getTargetTicks(),
                t.isCountUp(), t.isRunning(), t.isSilent(), t.getCommand());
    }
}