package com.mateof24.client;

public class ClientTimerManager {
    private static String currentTimerName = null;
    private static int hours = 0;
    private static int minutes = 0;
    private static int seconds = 0;
    private static boolean running = false;

    // Actualiza el timer
    public static void updateTimer(String name, int h, int m, int s, boolean run) {
        currentTimerName = name;
        hours = h;
        minutes = m;
        seconds = s;
        running = run;
    }

    // Pausa el timer
    public static void pauseTimer() {
        running = false;
    }

    // Elimina el timer
    public static void removeTimer() {
        currentTimerName = null;
        hours = 0;
        minutes = 0;
        seconds = 0;
        running = false;
    }

    // Tick del timer
    public static void tick() {
        if (running) {
            seconds++;
            if (seconds >= 60) {
                seconds = 0;
                minutes++;
                if (minutes >= 60) {
                    minutes = 0;
                    hours++;
                }
            }
        }
    }

    // Obtiene el display del timer
    public static String getDisplayTime() {
        if (currentTimerName == null) return null;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    public static boolean hasActiveTimer() {
        return currentTimerName != null && running;
    }

    public static String getCurrentTimerName() {
        return currentTimerName;
    }
}