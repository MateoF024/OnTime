package com.mateof24.render;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
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

    public static void updateTimer(String name, long current, long target, boolean up, boolean run, boolean sil, long servTick) {
        boolean isFirstUpdate = timerName.isEmpty() || !timerName.equals(name);

        timerName = name;
        currentTicks = current;
        targetTicks = target;
        countUp = up;
        running = run;
        silent = sil;
        serverTick = servTick;
        clientTickAtSync = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;

        if (isFirstUpdate || !running) {
            lastSecond = current / 20L;
        }

        pausedTicks = 0;
        wasPaused = false;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();

        boolean isPaused = mc.isPaused();

        if (isPaused) {
            if (!wasPaused) {
                pausedTicks = currentTicks;
                wasPaused = true;
            }
            return;
        }

        if (wasPaused) {
            currentTicks = pausedTicks;
            long currentGameTime = mc.level != null ? mc.level.getGameTime() : 0;
            long elapsedDuringPause = currentGameTime - clientTickAtSync;
            clientTickAtSync = currentGameTime - elapsedDuringPause;
            wasPaused = false;
        }

        if (!running || !visible) {
            return;
        }

        long currentSecond = getInterpolatedTicks() / 20L;

        if (!silent && currentSecond != lastSecond && lastSecond != -1) {
            if (mc.player != null && mc.level != null) {
                mc.level.playLocalSound(
                        mc.player.getX(),
                        mc.player.getY(),
                        mc.player.getZ(),
                        SoundEvents.NOTE_BLOCK_HAT.value(),
                        SoundSource.MASTER,
                        0.75F,
                        2.0F,
                        false
                );
            }
        }

        lastSecond = currentSecond;
    }

    public static long getInterpolatedTicks() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.isPaused() && wasPaused) {
            return pausedTicks;
        }

        if (!running) {
            return currentTicks;
        }

        long currentClientTick = mc.level != null ? mc.level.getGameTime() : 0;
        long ticksSinceSync = currentClientTick - clientTickAtSync;

        ticksSinceSync = Math.max(0, Math.min(ticksSinceSync, 40));

        if (countUp) {
            long interpolated = currentTicks + ticksSinceSync;
            return Math.min(interpolated, targetTicks);
        } else {
            long interpolated = currentTicks - ticksSinceSync;
            return Math.max(interpolated, 0);
        }
    }

    public static String getFormattedTime() {
        long totalSeconds = getInterpolatedTicks() / 20L;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    public static boolean shouldDisplay() {
        return !timerName.isEmpty() && visible;
    }

    public static void clear() {
        timerName = "";
        currentTicks = 0;
        targetTicks = 0;
        countUp = false;
        running = false;
        serverTick = 0;
        clientTickAtSync = 0;
        lastSecond = -1;
        pausedTicks = 0;
        wasPaused = false;
        silent = false;
        visible = true;
    }

    public static float getPercentage() {
        if (targetTicks == 0) return 100f;

        long ticks = getInterpolatedTicks();
        float percentage = (ticks * 100f) / targetTicks;

        if (countUp) {
            return 100f - percentage;
        } else {
            return percentage;
        }
    }

    public static String getTimerName() { return timerName; }
    public static boolean isRunning() { return running; }
    public static boolean isSilent() { return silent; }

    public static void setVisible(boolean vis) {
        visible = vis;
    }

    public static boolean isVisible() {
        return visible;
    }
}