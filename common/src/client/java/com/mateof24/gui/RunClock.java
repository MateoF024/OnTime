package com.mateof24.gui;

import com.mateof24.render.ClientRunView;
import com.mateof24.render.ClientTimerState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The time the panel shows, made to agree with the time on screen.
 *
 * <p>The panel is repainted from a server snapshot once a second. Drawing that
 * number straight puts the panel up to a second behind the counter in the
 * corner, which is small enough to look like nothing and large enough that the
 * tick you hear belongs to a different second than the one you are reading.</p>
 *
 * <p>So: whenever this client already tracks the run for the HUD, the panel
 * asks that same view for the time and the colour. Not a copy of its rules —
 * the view itself, so the two cannot drift apart or disagree about where a
 * threshold falls. An admin can also see runs they are not in the audience of,
 * and for those there is no HUD view; those are predicted here from the last
 * snapshot, which is the same arithmetic without the correction a stream of
 * packets would allow.</p>
 */
final class RunClock {

    private static final long NANOS_PER_TICK = 50_000_000L;

    /** runId to {ticks at the last change, nanoTime then, last raw value seen}. */
    private final Map<String, long[]> anchors = new HashMap<>();

    /**
     * Re-anchors the runs whose number actually moved.
     *
     * <p>Anchoring on every snapshot instead would restart the prediction each
     * second, so a clock would tick, freeze and jump rather than run.</p>
     */
    void onSnapshot(java.util.List<AdminModel.RunRow> rows) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (AdminModel.RunRow row : rows) {
            seen.add(row.runId());
            long[] anchor = anchors.get(row.runId());
            if (anchor == null || anchor[2] != row.currentTicks()) {
                anchors.put(row.runId(), new long[]{row.currentTicks(), System.nanoTime(), row.currentTicks()});
            }
        }
        anchors.keySet().retainAll(seen);
    }

    /** What this run's clock reads right now. */
    long ticks(AdminModel.RunRow row) {
        ClientRunView view = ClientTimerState.viewOf(parse(row.runId()));
        if (view != null) return view.getInterpolatedTicks();

        long[] anchor = anchors.get(row.runId());
        if (anchor == null) return row.currentTicks();
        if (!row.running()) return anchor[0];

        long elapsed = Math.max(0L, (System.nanoTime() - anchor[1]) / NANOS_PER_TICK);
        return row.countUp()
                ? Math.min(anchor[0] + elapsed, row.targetTicks())
                : Math.max(anchor[0] - elapsed, 0L);
    }

    /** The colour this run wears right now, by its own thresholds. */
    int color(AdminModel.RunRow row) {
        ClientRunView view = ClientTimerState.viewOf(parse(row.runId()));
        if (view != null) return view.currentColor();

        long current = ticks(row);
        float percentage = row.targetTicks() == 0 ? 100f : (current * 100f) / row.targetTicks();
        if (row.countUp()) percentage = 100f - percentage;
        if (percentage >= row.thresholdMid()) return row.colorHigh();
        if (percentage >= row.thresholdLow()) return row.colorMid();
        return row.colorLow();
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (Exception e) {
            return null;
        }
    }
}
