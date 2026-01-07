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
    private static long lastUpdateTime = 0;
    private static long serverTicks = 0;
    private static long lastSecond = -1;
    private static long pausedTicks = 0;
    private static boolean wasPaused = false;
    private static boolean silent = false;

    public static void updateTimer(String name, long current, long target, boolean up, boolean run, boolean sil) {
        timerName = name;
        serverTicks = current;
        currentTicks = current;
        targetTicks = target;
        countUp = up;
        running = run;
        silent = sil;
        lastUpdateTime = System.currentTimeMillis();
        lastSecond = current / 20L;
        pausedTicks = 0;
        wasPaused = false;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.isPaused()) {
            if (!wasPaused) {
                pausedTicks = getInterpolatedTicks();
                wasPaused = true;
            }
            return;
        }

        if (wasPaused) {
            lastUpdateTime = System.currentTimeMillis();
            serverTicks = pausedTicks;
            wasPaused = false;
        }

        if (!running) {
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

        long timeSinceUpdate = System.currentTimeMillis() - lastUpdateTime;
        long estimatedTicks = timeSinceUpdate / 50L;

        if (countUp) {
            long interpolated = serverTicks + estimatedTicks;
            return Math.min(interpolated, targetTicks);
        } else {
            long interpolated = serverTicks - estimatedTicks;
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
        return !timerName.isEmpty();
    }

    public static void clear() {
        timerName = "";
        currentTicks = 0;
        targetTicks = 0;
        countUp = false;
        running = false;
        lastUpdateTime = 0;
        serverTicks = 0;
        lastSecond = -1;
        pausedTicks = 0;
        wasPaused = false;
        silent = false;
    }

    public static String getTimerName() { return timerName; }
    public static boolean isRunning() { return running; }
    public static boolean isSilent() { return silent; }
}