package com.mateof24.trigger;

import com.mateof24.manager.TimerManager;
import com.mateof24.timer.Timer;

public final class TriggerDispatcher {

    private TriggerDispatcher() {}

    public static void dispatch(String type, String param) {
        if (type == null || type.isEmpty()) return;
        for (Timer t : TimerManager.getInstance().getAllTimers().values()) {
            String trigger = t.getTriggerType();
            if (trigger == null) continue;
            String action = t.getTriggerAction();
            if ("finish".equals(action) && !t.isRunning()) continue;
            if ("start".equals(action) && t.isRunning()) continue;
            if (matches(trigger, type, param)) {
                TriggerRegistry.fireFor(t.getName());
            }
        }
    }

    private static boolean matches(String configured, String type, String param) {
        if (configured.equals(type)) return true;
        if (param != null && configured.equals(type + ":" + param)) return true;
        return false;
    }
}
