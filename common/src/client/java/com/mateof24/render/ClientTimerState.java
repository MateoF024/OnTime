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

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.isPaused()) {
            for (ClientRunView view : views.values()) view.onClientPaused();
            return;
        }
        for (ClientRunView view : views.values()) view.onClientResumed();

        if (!visible || playerSilent) {
            // Still advance the cursors so resuming does not replay seconds.
            for (ClientRunView view : views.values()) view.advanceSecond();
            return;
        }

        // At most one tick sound per client tick: with several counters running
        // the second boundaries line up and the overlap would be a rattle.
        boolean play = false;
        for (ClientRunView view : views.values()) {
            if (view.advanceSecond()) play = true;
        }
        if (play && mc.player != null && mc.level != null) {
            VanillaClientCompat.playLocalTimerSound(displaySoundId, displaySoundVolume, displaySoundPitch);
        }
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
    }

    public static void setVisible(boolean vis) { visible = vis; }
    public static boolean isVisible() { return visible; }
    public static void setPlayerSilent(boolean sil) { playerSilent = sil; }
    public static boolean isPlayerSilent() { return playerSilent; }
}
