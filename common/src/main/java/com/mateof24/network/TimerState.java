package com.mateof24.network;

import com.mateof24.config.ModConfig;
import com.mateof24.manager.TimerManager;
import com.mateof24.platform.Services;
import com.mateof24.timer.Timer;
import com.mateof24.timer.TimerRun;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds what each player should see and decides when to push it.
 *
 * <p>4.0.0 broadcast one timer to everybody from about eighteen call sites,
 * each of them repeating the same six arguments. With audiences, one broadcast
 * cannot serve everyone any more, so those calls collapse into {@link
 * #markDirty()} and a single send at the end of the tick. Fewer places to
 * forget, and redundant sends within one tick disappear on the way.</p>
 */
public final class TimerState {

    private TimerState() {}

    private static boolean dirty = false;

    /** Requests a push at the end of this tick. Cheap and idempotent. */
    public static void markDirty() {
        dirty = true;
    }

    /** Sends the pending snapshot, if any. Called once per tick. */
    public static void flush(MinecraftServer server) {
        if (!dirty || server == null) return;
        dirty = false;
        Services.PLATFORM.sendTimerState(server);
    }

    /**
     * Runs this player should see, in a stable draw order.
     *
     * <p>Ordered by preset and then by run id so the z-order never flickers
     * between frames just because a map iterated differently.</p>
     */
    public static List<RunView> viewFor(UUID player) {
        List<RunView> views = new ArrayList<>();
        for (TimerRun run : TimerManager.getInstance().runsView()) {
            if (run.isAwaitingSequence()) continue;
            if (!run.isVisibleTo(player)) continue;
            views.add(toView(run));
        }
        views.sort(Comparator.comparing(RunView::preset).thenComparing(v -> v.runId().toString()));
        return views;
    }

    /**
     * Groups online players by the view they get, so one payload can be built
     * once and sent to everyone who shares it.
     *
     * <p>In the common case — a single global run — that is one payload for the
     * whole server, exactly as cheap as the old broadcast.</p>
     */
    public static Map<List<RunView>, List<UUID>> groupByView(Iterable<UUID> players) {
        Map<List<RunView>, List<UUID>> grouped = new LinkedHashMap<>();
        for (UUID player : players) {
            grouped.computeIfAbsent(viewFor(player), key -> new ArrayList<>()).add(player);
        }
        return grouped;
    }

    private static RunView toView(TimerRun run) {
        Timer timer = run.timer();
        ModConfig config = ModConfig.getInstance();

        String preset = timer.getPosition() != null
                ? timer.getPosition()
                : config.getPositionPreset().name();
        int x = timer.getTimerX() != null ? timer.getTimerX() : config.getTimerX();
        int y = timer.getTimerY() != null ? timer.getTimerY() : config.getTimerY();
        float scale = timer.getScale() != null ? timer.getScale() : config.getTimerScale();

        return new RunView(
                run.runId(),
                timer.getName(),
                run.getCurrentTicks(),
                run.getTargetTicks(),
                run.isCountUp(),
                run.isRunning(),
                run.isSilent(),
                orEmpty(timer.getTitleAbove()),
                orEmpty(timer.getTitleBelow()),
                orEmpty(timer.getTitleLeft()),
                orEmpty(timer.getTitleRight()),
                preset, x, y, scale);
    }

    private static String orEmpty(String value) {
        return value != null ? value : "";
    }
}
