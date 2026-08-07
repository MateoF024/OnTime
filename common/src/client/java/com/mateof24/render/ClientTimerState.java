package com.mateof24.render;

import com.mateof24.compat.VanillaClientCompat;
import com.mateof24.network.RunView;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the client knows about the executions it can see.
 *
 * <p>This used to hold exactly one timer's worth of fields. It now keeps a view
 * per execution — each with its own prediction anchor, titles, placement,
 * colours and sound. All that is left global is whether this player wants to
 * see the counters at all and whether they may hear them, which really are
 * properties of the player.</p>
 */
public class ClientTimerState {

    private static final Map<UUID, ClientRunView> views = new LinkedHashMap<>();

    private static boolean visible = true;
    private static boolean playerSilent = false;

    /**
     * Replaces the tracked executions with the server's snapshot, keeping the
     * prediction anchor of the ones that are still there.
     */
    public static void applyState(List<RunView> incoming) {
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (RunView view : incoming) {
            seen.add(view.runId());
            views.computeIfAbsent(view.runId(), ClientRunView::new).apply(view);
        }
        views.keySet().removeIf(id -> !seen.contains(id));
    }

    /** One tracked execution by id, or null when this client cannot see it. */
    public static ClientRunView viewOf(java.util.UUID runId) {
        return runId == null ? null : views.get(runId);
    }

    /** Executions to draw, in the order the server sent them. */
    public static Collection<ClientRunView> visibleViews() {
        if (!visible || views.isEmpty()) return List.of();
        return new ArrayList<>(views.values());
    }

    public static boolean shouldDisplay() {
        return visible && !placing && !views.isEmpty();
    }

    /**
     * True while the placement screen is up.
     *
     * <p>The real overlay is suppressed for exactly as long as that screen is
     * open, and nowhere else. Otherwise a timer that is running draws itself
     * behind the sample being dragged and the two read as one duplicated
     * counter.</p>
     */
    private static boolean placing;

    public static void setPlacing(boolean value) { placing = value; }

    /**
     * Minimum client ticks between two tick sounds.
     *
     * <p>A clock ticks every 20, and prediction jitter can make one land a tick
     * early, so anything comfortably under that is safe. Its only job is to
     * cover the instant the audible clock changes hands: the outgoing and the
     * incoming one are not in phase, and without this you could hear both
     * within the same second exactly once, at the handover.</p>
     */
    private static final int MIN_SOUND_GAP_TICKS = 15;

    private static int ticksSinceSound = MIN_SOUND_GAP_TICKS;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.isPaused()) {
            for (ClientRunView view : views.values()) view.onClientPaused();
            return;
        }
        for (ClientRunView view : views.values()) view.onClientResumed();

        if (ticksSinceSound < MIN_SOUND_GAP_TICKS) ticksSinceSound++;

        if (!visible || playerSilent) {
            // Still advance the cursors so resuming does not replay seconds.
            for (ClientRunView view : views.values()) view.advanceSecond();
            return;
        }

        // Exactly one clock is audible at a time. Every cursor still advances —
        // they must, or a run would replay seconds when it becomes the audible
        // one — but only the elected run's crossing makes a sound.
        ClientRunView sounding = electSoundingRun();
        boolean play = false;
        for (ClientRunView view : views.values()) {
            boolean crossed = view.advanceSecond();
            if (crossed && view == sounding) play = true;
        }

        // The sound is the elected run's own, not a server-wide one: two timers
        // may legitimately want different clocks, and only one of them is
        // audible at a time anyway.
        if (play && sounding != null && ticksSinceSound >= MIN_SOUND_GAP_TICKS
                && mc.player != null && mc.level != null) {
            VanillaClientCompat.playLocalTimerSound(
                    sounding.soundId(), sounding.soundVolume(), sounding.soundPitch());
            ticksSinceSound = 0;
        }
    }

    /**
     * The one execution allowed to make a sound: the one closest to ending.
     *
     * <p>Two clocks ticking at once are not twice the information, they are a
     * rattle — a person can follow one cadence, and which counter a given tick
     * belonged to was never audible anyway. So one is picked, and the rest are
     * drawn in silence.</p>
     *
     * <p>Closest to ending is the useful one: it is the next thing that will
     * actually happen. A one-hour game and a one-minute turn running together
     * means you hear the turn, and when the hour drops under a minute left it
     * takes over on its own — no special case, it simply became the nearer of
     * the two.</p>
     *
     * <p>Re-elected every tick, which is safe precisely because the key only
     * moves one way: remaining time falls at the same rate for every running
     * clock, so once one is ahead it stays ahead. It changes hands only on a
     * real event — a run starts, ends, is paused, is given time, laps — and
     * never oscillates. Ties break on run id so two identical clocks still
     * settle on one.</p>
     *
     * @return null when nothing may sound, which silences the lot
     */
    static ClientRunView electSoundingRun() {
        ClientRunView best = null;
        long bestRemaining = Long.MAX_VALUE;
        for (ClientRunView view : views.values()) {
            if (!view.isRunning() || view.isSilent()) continue;
            long remaining = view.remainingTicks();
            if (best == null || remaining < bestRemaining
                    || (remaining == bestRemaining
                        && view.runId().compareTo(best.runId()) < 0)) {
                best = view;
                bestRemaining = remaining;
            }
        }
        return best;
    }

    public static String formatTicks(long ticks) {
        long totalSeconds = ticks / 20L;
        long hours = totalSeconds / 3600, minutes = (totalSeconds % 3600) / 60, seconds = totalSeconds % 60;
        return hours > 0 ? String.format("%02d:%02d:%02d", hours, minutes, seconds)
                : String.format("%02d:%02d", minutes, seconds);
    }

    public static void clear() {
        views.clear();
        visible = true;
        playerSilent = false;
        ticksSinceSound = MIN_SOUND_GAP_TICKS;
    }

    public static void setVisible(boolean vis) { visible = vis; }
    public static boolean isVisible() { return visible; }
    public static void setPlayerSilent(boolean sil) { playerSilent = sil; }
    public static boolean isPlayerSilent() { return playerSilent; }
}
