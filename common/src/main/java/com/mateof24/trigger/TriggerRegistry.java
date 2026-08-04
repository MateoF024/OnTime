package com.mateof24.trigger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Timers whose trigger has fired and is waiting to be consumed by the tick.
 *
 * <p>Each entry carries the player who caused it, when there was one. Nothing
 * reads that yet — it is what per-player runs will need in order to know which
 * execution a death or a dimension change belongs to — but it has to be
 * recorded at the moment the event happens, not reconstructed later.</p>
 */
public class TriggerRegistry {

    /** A pending trigger. {@code causedBy} is null for events with no player behind them. */
    public record Fire(UUID causedBy) {}

    private static final Map<String, Fire> firedTimers = new ConcurrentHashMap<>();

    public static void fireFor(String timerName) {
        fireFor(timerName, null);
    }

    public static void fireFor(String timerName, UUID causedBy) {
        if (timerName == null || timerName.isEmpty()) return;
        firedTimers.put(timerName, new Fire(causedBy));
    }

    /** Consumes the pending trigger, or null when nothing fired. */
    public static Fire consume(String timerName) {
        if (timerName == null || timerName.isEmpty()) return null;
        return firedTimers.remove(timerName);
    }

    public static boolean consumeFor(String timerName) {
        return consume(timerName) != null;
    }

    public static void resetFor(String timerName) {
        if (timerName == null || timerName.isEmpty()) return;
        firedTimers.remove(timerName);
    }

    public static void resetAll() {
        firedTimers.clear();
    }
}
