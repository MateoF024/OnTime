package com.mateof24.render;

import com.mateof24.config.TimerPositionPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class ClientTimerState {
    private static String timerName = "";
    private static long currentTicks = 0;
    private static long targetTicks = 0;
    private static boolean countUp = false;
    private static boolean running = false;
    private static long serverTick = 0;
    private static long clientTickAtSync = 0;
    private static long lastSecond = -1;
    private static long pausedTicks = 0;
    private static boolean wasPaused = false;
    private static boolean silent = false;
    private static boolean visible = true;
    private static boolean playerSilent = false;

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

    public static void updateDisplayConfig(int x, int y, String preset, float scale,
                                           int colorHigh, int colorMid, int colorLow,
                                           int thresholdMid, int thresholdLow,
                                           String soundId, float soundVolume, float soundPitch) {
        displayX = x; displayY = y; displayPreset = preset; displayScale = scale;
        displayColorHigh = colorHigh; displayColorMid = colorMid; displayColorLow = colorLow;
        displayThresholdMid = thresholdMid; displayThresholdLow = thresholdLow;
        displaySoundId = soundId; displaySoundVolume = soundVolume; displaySoundPitch = soundPitch;
    }

    public static void updateTimer(String name, long current, long target, boolean up, boolean run, boolean sil, long servTick) {
        boolean isFirstUpdate = timerName.isEmpty() || !timerName.equals(name);
        timerName = name; currentTicks = current; targetTicks = target;
        countUp = up; running = run; silent = sil; serverTick = servTick;
        clientTickAtSync = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
        if (isFirstUpdate || !running) lastSecond = current / 20L;
        pausedTicks = 0; wasPaused = false;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean isPaused = mc.isPaused();

        if (isPaused) {
            if (!wasPaused) { pausedTicks = currentTicks; wasPaused = true; }
            return;
        }

        if (wasPaused) {
            currentTicks = pausedTicks;
            long currentGameTime = mc.level != null ? mc.level.getGameTime() : 0;
            long elapsedDuringPause = currentGameTime - clientTickAtSync;
            clientTickAtSync = currentGameTime - elapsedDuringPause;
            wasPaused = false;
        }

        if (!running || !visible) return;

        long currentSecond = getInterpolatedTicks() / 20L;
        if (!silent && !playerSilent && currentSecond != lastSecond && lastSecond != -1) {
            if (mc.player != null && mc.level != null) playTimerSound();
        }
        lastSecond = currentSecond;
    }

    private static void playTimerSound() {
        Minecraft mc = Minecraft.getInstance();
        try {
            ResourceLocation soundLocation = ResourceLocation.tryParse(displaySoundId);
            SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(soundLocation);
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    soundEvent, SoundSource.MASTER, displaySoundVolume, displaySoundPitch, false);
        } catch (Exception e) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.value(),
                    SoundSource.MASTER, 0.75F, 2.0F, false);
        }
    }

    public static long getInterpolatedTicks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() && wasPaused) return pausedTicks;
        if (!running) return currentTicks;

        long currentClientTick = mc.level != null ? mc.level.getGameTime() : 0;
        long ticksSinceSync = Math.max(0, Math.min(currentClientTick - clientTickAtSync, 40));

        if (countUp) return Math.min(currentTicks + ticksSinceSync, targetTicks);
        else return Math.max(currentTicks - ticksSinceSync, 0);
    }

    public static String getFormattedTime() {
        long totalSeconds = getInterpolatedTicks() / 20L;
        long hours = totalSeconds / 3600, minutes = (totalSeconds % 3600) / 60, seconds = totalSeconds % 60;
        return hours > 0 ? String.format("%02d:%02d:%02d", hours, minutes, seconds)
                : String.format("%02d:%02d", minutes, seconds);
    }

    public static int getColorForPercentage(float percentage) {
        if (percentage >= displayThresholdMid) return displayColorHigh;
        else if (percentage >= displayThresholdLow) return displayColorMid;
        else return displayColorLow;
    }

    public static boolean shouldDisplay() { return !timerName.isEmpty() && visible; }

    public static void clear() {
        timerName = ""; currentTicks = 0; targetTicks = 0; countUp = false; running = false;
        serverTick = 0; clientTickAtSync = 0; lastSecond = -1;
        pausedTicks = 0; wasPaused = false; silent = false; visible = true; playerSilent = false;
    }

    public static float getPercentage() {
        if (targetTicks == 0) return 100f;
        float percentage = (getInterpolatedTicks() * 100f) / targetTicks;
        return countUp ? 100f - percentage : percentage;
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