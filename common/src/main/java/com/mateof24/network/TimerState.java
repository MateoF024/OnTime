package com.mateof24.network;

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
    /** Ordered by preset then run id, so the z-order cannot flicker. */
    private static final Comparator<RunView> DRAW_ORDER =
            Comparator.comparing(RunView::preset).thenComparing(v -> v.runId().toString());

    public static List<RunView> viewFor(UUID player) {
        List<RunView> views = new ArrayList<>();
        for (TimerRun run : TimerManager.getInstance().runsView()) {
            if (run.isAwaitingSequence()) continue;
            if (!isVisibleNow(run)) continue;
            if (!run.isVisibleTo(player)) continue;
            views.add(toView(run));
        }
        views.sort(DRAW_ORDER);
        return views;
    }

    /**
     * Groups online players by the view they get, so one payload can be built
     * once and sent to everyone who shares it.
     *
     * <p>In the common case — a single global run — that is one payload for the
     * whole server, exactly as cheap as the old broadcast.</p>
     *
     * <p>Built by walking the runs and handing each one to its viewers, rather
     * than asking every player which runs they see. Both answer the same
     * question, but the second builds one {@link RunView} per player per run:
     * a hundred per-player runs plus a hundred players is ten thousand objects
     * a second for a hundred distinct payloads. This way each run is converted
     * exactly once, and a global run's view is the same instance in every
     * list.</p>
     */
    public static Map<List<RunView>, List<UUID>> groupByView(Iterable<UUID> players) {
        Map<UUID, List<RunView>> perPlayer = new LinkedHashMap<>();
        for (UUID player : players) perPlayer.put(player, new ArrayList<>());

        for (TimerRun run : TimerManager.getInstance().runsView()) {
            if (run.isAwaitingSequence()) continue;
            if (!isVisibleNow(run)) continue;
            RunView view = toView(run);
            if (run.audience().isGlobal()) {
                for (List<RunView> seen : perPlayer.values()) seen.add(view);
            } else {
                for (UUID member : run.audience().players()) {
                    List<RunView> seen = perPlayer.get(member);
                    // Offline members of a fixed audience simply have no list.
                    if (seen != null) seen.add(view);
                }
            }
        }

        Map<List<RunView>, List<UUID>> grouped = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<RunView>> entry : perPlayer.entrySet()) {
            entry.getValue().sort(DRAW_ORDER);
            grouped.computeIfAbsent(entry.getValue(), key -> new ArrayList<>()).add(entry.getKey());
        }
        return grouped;
    }

    /**
     * Whether this run is drawn at all right now.
     *
     * <p>A counter waiting out a repeat or a hand-over sits there stopped,
     * which reads as one that has broken — the only way to tell was to wait
     * and see whether it started again. Hidden means not sent: there is
     * nothing for the client to decide, and nothing to go stale if the
     * connection is quiet.</p>
     */
    private static boolean isVisibleNow(TimerRun run) {
        return !run.isInCooldown() || !run.timer().display().hideOnCooldown();
    }

    private static RunView toView(TimerRun run) {
        Timer timer = run.timer();
        com.mateof24.timer.TimerDisplay display = timer.display();
        String preset = com.mateof24.manager.DisplaySlots.presetOf(timer);

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
                preset, display.x(), display.y(), display.scale(),
                display.colorHigh(), display.colorMid(), display.colorLow(),
                display.thresholdMid(), display.thresholdLow(),
                display.soundId(), display.soundVolume(), display.soundPitch());
    }

    private static String orEmpty(String value) {
        return value != null ? value : "";
    }
}
