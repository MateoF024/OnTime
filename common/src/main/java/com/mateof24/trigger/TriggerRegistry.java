package com.mateof24.trigger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TriggerRegistry {

    private static final Set<String> firedTimers = ConcurrentHashMap.newKeySet();

    public static void fireFor(String timerName) {
        if (timerName == null || timerName.isEmpty()) return;
        firedTimers.add(timerName);
    }

    public static boolean consumeFor(String timerName) {
        if (timerName == null || timerName.isEmpty()) return false;
        return firedTimers.remove(timerName);
    }

    public static void resetFor(String timerName) {
        if (timerName == null || timerName.isEmpty()) return;
        firedTimers.remove(timerName);
    }

    public static void resetAll() {
        firedTimers.clear();
    }
}
