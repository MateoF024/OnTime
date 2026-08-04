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
 * per execution — each with its own prediction anchor, titles and placement —
 * while the display defaults, the per-player visibility flag and the sound
 * settings stay global, because they are.</p>
 */
public class ClientTimerState {

    private static final Map<UUID, ClientRunView> views = new LinkedHashMap<>();

    private static boolean visible = true;
    private static boolean playerSilent = false;

    private static int displayColorHigh = 0xFFFFFF;
    private static int displayColorMid = 0xFFFF00;
    private static int displayColorLow = 0xFF0000;
    private static int displayThresholdMid = 30;
    private static int displayThresholdLow = 10;
    private static String displaySoundId = "minecraft:block.note_block.hat";
    private static float displaySoundVolume = 1.0f;
    private static float displaySoundPitch = 2.0f;

    public static void updateDisplayConfig(int x, int y, String preset, float scale,
                                           int colorHigh, int colorMid, int colorLow,
                                           int thresholdMid, int thresholdLow,
                                           String soundId, float soundVolume, float soundPitch) {
        // Position and scale now ride the state snapshot, already resolved per
        // timer, so only the genuinely global settings are taken from here.
        displayColorHigh = colorHigh; displayColorMid = colorMid; displayColorLow = colorLow;
        displayThresholdMid = thresholdMid; displayThresholdLow = thresholdLow;
        displaySoundId = soundId; displaySoundVolume = soundVolume; displaySoundPitch = soundPitch;
    }

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

    /** Executions to draw, in the order the server sent them. */
    public static Collection<ClientRunView> visibleViews() {
        if (!visible || views.isEmpty()) return List.of();
        return new ArrayList<>(views.values());
    }

    public static boolean shouldDisplay() {
        return visible && !views.isEmpty();
    }

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

        if (play && ticksSinceSound >= MIN_SOUND_GAP_TICKS
                && mc.player != null && mc.level != null) {
            VanillaClientCompat.playLocalTimerSound(displaySoundId, displaySoundVolume, displaySoundPitch);
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

    public static int getColorForPercentage(float percentage) {
        if (percentage >= displayThresholdMid) return displayColorHigh;
        else if (percentage >= displayThresholdLow) return displayColorMid;
        else return displayColorLow;
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
