package com.mateof24.event;

import com.mateof24.api.ApiViews;
import com.mateof24.api.TimerInfo;
import com.mateof24.api.TimerRunInfo;
import com.mateof24.timer.TimerRun;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Timer events, delivered per execution.
 *
 * <p>Every event now carries the execution it happened to. That is the whole
 * difference from 4.0.0: a listener told "a timer finished" could not act on it
 * when three executions of that timer were in flight, because it had no way to
 * ask which one — and with per-player runs, "which one" is usually the only
 * thing the listener needs.</p>
 *
 * <p>The old {@link TimerInfo} listeners still fire, once per execution, so a
 * 4.0.0 consumer keeps working. They are built from the run rather than from
 * the definition's mirrored fields, which is the closest the old shape gets to
 * being true.</p>
 */
public class TimerEventBus {

    private static final List<Consumer<TimerRunInfo>> onRunStart = new CopyOnWriteArrayList<>();
    private static final List<Consumer<TimerRunInfo>> onRunFinish = new CopyOnWriteArrayList<>();
    private static final List<Consumer<TimerRunInfo>> onRunPause = new CopyOnWriteArrayList<>();
    private static final List<Consumer<TimerRunInfo>> onRunResume = new CopyOnWriteArrayList<>();
    private static final List<Consumer<TimerRunInfo>> onRunTick = new CopyOnWriteArrayList<>();

    @SuppressWarnings("deprecation")
    private static final List<Consumer<TimerInfo>> legacyStart = new CopyOnWriteArrayList<>();
    @SuppressWarnings("deprecation")
    private static final List<Consumer<TimerInfo>> legacyFinish = new CopyOnWriteArrayList<>();
    @SuppressWarnings("deprecation")
    private static final List<Consumer<TimerInfo>> legacyPause = new CopyOnWriteArrayList<>();
    @SuppressWarnings("deprecation")
    private static final List<Consumer<TimerInfo>> legacyResume = new CopyOnWriteArrayList<>();
    @SuppressWarnings("deprecation")
    private static final List<Consumer<TimerInfo>> legacyTick = new CopyOnWriteArrayList<>();

    public static void registerOnRunStart(Consumer<TimerRunInfo> listener) { onRunStart.add(listener); }
    public static void registerOnRunFinish(Consumer<TimerRunInfo> listener) { onRunFinish.add(listener); }
    public static void registerOnRunPause(Consumer<TimerRunInfo> listener) { onRunPause.add(listener); }
    public static void registerOnRunResume(Consumer<TimerRunInfo> listener) { onRunResume.add(listener); }
    public static void registerOnRunTick(Consumer<TimerRunInfo> listener) { onRunTick.add(listener); }

    @Deprecated public static void registerOnStart(Consumer<TimerInfo> l) { legacyStart.add(l); }
    @Deprecated public static void registerOnFinish(Consumer<TimerInfo> l) { legacyFinish.add(l); }
    @Deprecated public static void registerOnPause(Consumer<TimerInfo> l) { legacyPause.add(l); }
    @Deprecated public static void registerOnResume(Consumer<TimerInfo> l) { legacyResume.add(l); }
    @Deprecated public static void registerOnTick(Consumer<TimerInfo> l) { legacyTick.add(l); }

    public static void fireStart(TimerRun run) { dispatch(run, onRunStart, legacyStart); }
    public static void fireFinish(TimerRun run) { dispatch(run, onRunFinish, legacyFinish); }
    public static void firePause(TimerRun run) { dispatch(run, onRunPause, legacyPause); }
    public static void fireResume(TimerRun run) { dispatch(run, onRunResume, legacyResume); }
    public static void fireTick(TimerRun run) { dispatch(run, onRunTick, legacyTick); }

    /**
     * Builds each snapshot at most once and only when somebody is listening,
     * which matters for the tick event: it fires for every running execution
     * every second, and most servers have no listener at all.
     */
    private static void dispatch(TimerRun run, List<Consumer<TimerRunInfo>> modern,
                                 List<Consumer<TimerInfo>> legacy) {
        if (!modern.isEmpty()) {
            TimerRunInfo info = ApiViews.of(run);
            for (Consumer<TimerRunInfo> listener : modern) fire(listener, info);
        }
        if (!legacy.isEmpty()) {
            TimerInfo info = ApiViews.legacyOf(run);
            for (Consumer<TimerInfo> listener : legacy) fire(listener, info);
        }
    }

    private static <T> void fire(Consumer<T> listener, T info) {
        try { listener.accept(info); }
        catch (Exception e) {
            com.mateof24.OnTimeConstants.LOGGER.warn("TimerEventBus listener threw an exception", e);
        }
    }
}
