package com.mateof24.render;

import com.mateof24.config.TimerPositionPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class ClientTimerState {
    private static String timerName = "";
    private static long targetTicks = 0;
    private static boolean countUp = false;
    private static boolean running = false;
    private static boolean silent = false;
    private static boolean visible = true;
    private static boolean playerSilent = false;

    // Anchor for nanoTime-based prediction. Using wall-clock time instead of
    // mc.level.getGameTime() avoids the ±1 second jitter that happens when
    // sync packets arrive 1-2 ticks early/late on the client tick clock.
    private static long currentTicksAtSync = 0;
    private static long realTimeAtSyncNanos = 0;
    private static boolean hasSync = false;

    private static long pauseStartedAtNanos = 0;
    private static long pausedTicksSnapshot = 0;
    private static boolean wasPaused = false;

    private static long lastSecond = -1;

    private static int displayX = -1;
    private static int displayY = 4;
    private static String displayPreset = "BOSSBAR";
    private static float displayScale = 1.0f;
    private static int displayColorHigh = 0xFFFFFF;
    private static int displayColorMid = 0xFFFF00;
    private static int displayColorLow = 0xFF0000;
    private static int displayThresholdMid = 30;
    private static int displayThresholdLow = 10;
    private static String displaySoundId = "minecraft:block.note_block.hat";
    private static float displaySoundVolume = 1.0f;
    private static float displaySoundPitch = 2.0f;

    private static final long NANOS_PER_TICK = 50_000_000L;
    // Beyond this gap between predicted and received ticks we snap; otherwise
    // we clamp the visible correction to ±1 tick so the displayed second
    // stays monotonic under network jitter.
    private static final long SNAP_THRESHOLD_TICKS = 20;

    public static void updateDisplayConfig(int x, int y, String preset, float scale,
                                           int colorHigh, int colorMid, int colorLow,
                                           int thresholdMid, int thresholdLow,
                                           String soundId, float soundVolume, float soundPitch) {
        displayX = x; displayY = y; displayPreset = preset; displayScale = scale;
        displayColorHigh = colorHigh; displayColorMid = colorMid; displayColorLow = colorLow;
        displayThresholdMid = thresholdMid; displayThresholdLow = thresholdLow;
        displaySoundId = soundId; displaySoundVolume = soundVolume; displaySoundPitch = soundPitch;
    }

    public static void updateTimer(String name, long current, long target, boolean up,
                                   boolean run, boolean sil) {
        long now = System.nanoTime();
        boolean firstSync = !hasSync;
        boolean nameChanged = !timerName.equals(name);
        boolean wasRunning = running;

        timerName = name;
        targetTicks = target;
        countUp = up;
        silent = sil;

        boolean shouldSnap = firstSync || nameChanged || !run || !wasRunning;

        if (shouldSnap) {
            currentTicksAtSync = current;
            realTimeAtSyncNanos = now;
            if (firstSync || nameChanged || !run) lastSecond = current / 20L;
        } else {
            long predicted = computeTicksAt(now);
            long diff = current - predicted;
            long absDiff = Math.abs(diff);

            if (absDiff > SNAP_THRESHOLD_TICKS) {
                currentTicksAtSync = current;
                realTimeAtSyncNanos = now;
                lastSecond = current / 20L;
            } else if (absDiff == 0) {
                currentTicksAtSync = current;
                realTimeAtSyncNanos = now;
            } else {
                long step = Long.signum(diff);
                currentTicksAtSync = predicted + step;
                realTimeAtSyncNanos = now;
            }
        }

        running = run;
        wasPaused = false;
        hasSync = true;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean isPaused = mc.isPaused();

        if (isPaused) {
            if (!wasPaused) {
                pausedTicksSnapshot = computeTicksAt(System.nanoTime());
                pauseStartedAtNanos = System.nanoTime();
                wasPaused = true;
            }
            return;
        }

        if (wasPaused) {
            // Shift the anchor forward so prediction continues from where it stopped.
            long pauseDuration = System.nanoTime() - pauseStartedAtNanos;
            realTimeAtSyncNanos += pauseDuration;
            wasPaused = false;
        }

        if (!running || !visible) return;

        long currentSecond = computeTicksAt(System.nanoTime()) / 20L;
        if (!silent && !playerSilent && currentSecond != lastSecond && lastSecond != -1) {
            if (mc.player != null && mc.level != null) playTimerSound();
        }
        lastSecond = currentSecond;
    }

    private static ResourceLocation parseSound(String id) {
        return ResourceLocation.tryParse(id);
    }

    private static void playTimerSound() {
        Minecraft mc = Minecraft.getInstance();
        try {
            SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(parseSound(displaySoundId));
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    soundEvent, SoundSource.MASTER, displaySoundVolume, displaySoundPitch, false);
        } catch (Exception e) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.value(),
                    SoundSource.MASTER, 0.75F, 2.0F, false);
        }
    }

    private static long computeTicksAt(long nowNanos) {
        if (!hasSync) return 0;
        if (wasPaused) return pausedTicksSnapshot;
        if (!running) return currentTicksAtSync;
        long elapsedTicks = (nowNanos - realTimeAtSyncNanos) / NANOS_PER_TICK;
        if (elapsedTicks < 0) elapsedTicks = 0;
        if (countUp) return Math.min(currentTicksAtSync + elapsedTicks, targetTicks);
        return Math.max(currentTicksAtSync - elapsedTicks, 0);
    }

    public static long getInterpolatedTicks() {
        return computeTicksAt(System.nanoTime());
    }

    public static String formatTicks(long ticks) {
        long totalSeconds = ticks / 20L;
        long hours = totalSeconds / 3600, minutes = (totalSeconds % 3600) / 60, seconds = totalSeconds % 60;
        return hours > 0 ? String.format("%02d:%02d:%02d", hours, minutes, seconds)
                : String.format("%02d:%02d", minutes, seconds);
    }

    public static float percentageOf(long ticks) {
        if (targetTicks == 0) return 100f;
        float percentage = (ticks * 100f) / targetTicks;
        return countUp ? 100f - percentage : percentage;
    }

    public static String getFormattedTime() { return formatTicks(getInterpolatedTicks()); }

    public static float getPercentage() { return percentageOf(getInterpolatedTicks()); }

    public static int getColorForPercentage(float percentage) {
        if (percentage >= displayThresholdMid) return displayColorHigh;
        else if (percentage >= displayThresholdLow) return displayColorMid;
        else return displayColorLow;
    }

    public static boolean shouldDisplay() { return !timerName.isEmpty() && visible; }

    public static void clear() {
        timerName = "";
        targetTicks = 0;
        countUp = false;
        running = false;
        silent = false;
        visible = true;
        playerSilent = false;
        currentTicksAtSync = 0;
        realTimeAtSyncNanos = 0;
        hasSync = false;
        pauseStartedAtNanos = 0;
        pausedTicksSnapshot = 0;
        wasPaused = false;
        lastSecond = -1;
    }

    public static TimerPositionPreset getPositionPreset() { return TimerPositionPreset.fromString(displayPreset); }
    public static int getDisplayX() { return displayX; }
    public static int getDisplayY() { return displayY; }
    public static float getDisplayScale() { return displayScale; }
    public static String getTimerName() { return timerName; }
    public static boolean isRunning() { return running; }
    public static boolean isSilent() { return silent; }
    public static void setVisible(boolean vis) { visible = vis; }
    public static boolean isVisible() { return visible; }
    public static void setPlayerSilent(boolean sil) { playerSilent = sil; }
    public static boolean isPlayerSilent() { return playerSilent; }
}
