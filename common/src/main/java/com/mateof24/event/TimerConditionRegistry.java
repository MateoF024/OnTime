package com.mateof24.event;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class TimerConditionRegistry {

    private static final Map<String, Supplier<Boolean>> conditions = new ConcurrentHashMap<>();

    public static void register(String timerName, Supplier<Boolean> condition) {
        conditions.put(timerName, condition);
    }

    public static void unregister(String timerName) {
        conditions.remove(timerName);
    }

    public static boolean evaluate(String timerName) {
        Supplier<Boolean> condition = conditions.get(timerName);
        if (condition == null) return false;
        try { return Boolean.TRUE.equals(condition.get()); }
        catch (Exception e) {
            com.mateof24.OnTimeConstants.LOGGER.warn("External condition for timer '{}' threw an exception", timerName, e);
            return false;
        }
    }

    public static boolean hasCondition(String timerName) {
        return conditions.containsKey(timerName);
    }
}