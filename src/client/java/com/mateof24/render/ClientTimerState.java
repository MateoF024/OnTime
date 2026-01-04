package com.mateof24.render;

public class ClientTimerState {
    private static String timerName = "";
    private static long currentTicks = 0;
    private static long targetTicks = 0;
    private static boolean countUp = false;
    private static boolean running = false;
    private static long lastUpdateTime = 0;
    private static long serverTicks = 0;

    // Update timer state from server
    public static void updateTimer(String name, long current, long target, boolean up, boolean run) {
        timerName = name;
        serverTicks = current;
        currentTicks = current;
        targetTicks = target;
        countUp = up;
        running = run;
        lastUpdateTime = System.currentTimeMillis();
    }

    // Get interpolated current time for smooth rendering
    public static long getInterpolatedTicks() {
        if (!running) {
            return currentTicks;
        }

        long timeSinceUpdate = System.currentTimeMillis() - lastUpdateTime;
        long estimatedTicks = timeSinceUpdate / 50L; // 50ms per tick

        if (countUp) {
            long interpolated = serverTicks + estimatedTicks;
            return Math.min(interpolated, targetTicks);
        } else {
            long interpolated = serverTicks - estimatedTicks;
            return Math.max(interpolated, 0);
        }
    }

    // Get formatted time string
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

    // Check if timer should be displayed
    public static boolean shouldDisplay() {
        return running && !timerName.isEmpty();
    }

    // Clear timer state
    public static void clear() {
        timerName = "";
        currentTicks = 0;
        targetTicks = 0;
        countUp = false;
        running = false;
        lastUpdateTime = 0;
        serverTicks = 0;
    }

    // Getters
    public static String getTimerName() { return timerName; }
    public static boolean isRunning() { return running; }
}