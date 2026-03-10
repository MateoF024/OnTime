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
                t.isCountUp(), t.isRunning(), t.isSilent(), t.getCommand(),
                t.isRepeat(), t.getRepeatCount(), t.getRepeatsDone());
    }

    public boolean setTimerCommand(String name, String command) {
        return TimerManager.getInstance().setTimerCommand(name, command);
    }

    public boolean setTimerRepeat(String name, boolean repeat, int count) {
        return TimerManager.getInstance().getTimer(name).map(t -> {
            t.setRepeat(repeat);
            t.setRepeatCount(count);
            TimerManager.getInstance().saveTimers();
            return true;
        }).orElse(false);
    }

    public static final String SCOREBOARD_OBJECTIVE = "ontime_active";

    public String getScoreboardObjectiveName() {
        return SCOREBOARD_OBJECTIVE;
    }

    public void registerOnStart(java.util.function.Consumer<TimerInfo> listener) {
        com.mateof24.event.TimerEventBus.registerOnStart(listener);
    }
    public void registerOnFinish(java.util.function.Consumer<TimerInfo> listener) {
        com.mateof24.event.TimerEventBus.registerOnFinish(listener);
    }
    public void registerOnPause(java.util.function.Consumer<TimerInfo> listener) {
        com.mateof24.event.TimerEventBus.registerOnPause(listener);
    }
    public void registerOnResume(java.util.function.Consumer<TimerInfo> listener) {
        com.mateof24.event.TimerEventBus.registerOnResume(listener);
    }
    public void registerOnTick(java.util.function.Consumer<TimerInfo> listener) {
        com.mateof24.event.TimerEventBus.registerOnTick(listener);
    }

    public void registerFinishCondition(String timerName, java.util.function.Supplier<Boolean> condition) {
        com.mateof24.event.TimerConditionRegistry.register(timerName, condition);
    }

    public void unregisterFinishCondition(String timerName) {
        com.mateof24.event.TimerConditionRegistry.unregister(timerName);
    }

    public void setPermissionProvider(com.mateof24.permission.PermissionHelper.IPermissionProvider provider) {
        com.mateof24.permission.PermissionHelper.setProvider(provider);
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