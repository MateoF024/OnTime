package com.mateof24.event;

import com.mateof24.api.TimerInfo;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class TimerEventBus {

    private static final List<Consumer<TimerInfo>> onStartListeners = new CopyOnWriteArrayList<>();
    private static final List<Consumer<TimerInfo>> onFinishListeners = new CopyOnWriteArrayList<>();
    private static final List<Consumer<TimerInfo>> onPauseListeners = new CopyOnWriteArrayList<>();
    private static final List<Consumer<TimerInfo>> onResumeListeners = new CopyOnWriteArrayList<>();
    private static final List<Consumer<TimerInfo>> onTickListeners = new CopyOnWriteArrayList<>();

    public static void registerOnStart(Consumer<TimerInfo> listener) { onStartListeners.add(listener); }
    public static void registerOnFinish(Consumer<TimerInfo> listener) { onFinishListeners.add(listener); }
    public static void registerOnPause(Consumer<TimerInfo> listener) { onPauseListeners.add(listener); }
    public static void registerOnResume(Consumer<TimerInfo> listener) { onResumeListeners.add(listener); }
    public static void registerOnTick(Consumer<TimerInfo> listener) { onTickListeners.add(listener); }

    public static void fireOnStart(TimerInfo info) { onStartListeners.forEach(l -> fire(l, info)); }
    public static void fireOnFinish(TimerInfo info) { onFinishListeners.forEach(l -> fire(l, info)); }
    public static void fireOnPause(TimerInfo info) { onPauseListeners.forEach(l -> fire(l, info)); }
    public static void fireOnResume(TimerInfo info) { onResumeListeners.forEach(l -> fire(l, info)); }
    public static void fireOnTick(TimerInfo info) { onTickListeners.forEach(l -> fire(l, info)); }

    private static void fire(Consumer<TimerInfo> listener, TimerInfo info) {
        try { listener.accept(info); }
        catch (Exception e) {
            com.mateof24.OnTimeConstants.LOGGER.warn("TimerEventBus listener threw an exception", e);
        }
    }
}