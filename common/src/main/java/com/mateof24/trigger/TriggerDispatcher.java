package com.mateof24.trigger;

import com.mateof24.manager.TimerManager;
import com.mateof24.timer.Timer;

public final class TriggerDispatcher {

    private TriggerDispatcher() {}

    public static void dispatch(String type, String param) {
        dispatch(type, param, null);
    }

    /**
     * @param causedBy player behind the event, when there is one. Recorded now
     *                 so per-player runs can later tell which execution the
     *                 event belongs to.
     */
    public static void dispatch(String type, String param, java.util.UUID causedBy) {
        if (type == null || type.isEmpty()) return;
        for (Timer t : TimerManager.getInstance().timersView()) {
            String trigger = t.getTriggerType();
            if (trigger == null) continue;
            String action = t.getTriggerAction();
            if ("finish".equals(action) && !t.isRunning()) continue;
            if ("start".equals(action) && t.isRunning()) continue;
            if (matches(trigger, type, param)) {
                TriggerRegistry.fireFor(t.getName(), causedBy);
            }
        }
    }

    private static boolean matches(String configured, String type, String param) {
        if (configured.equals(type)) return true;
        if (param != null && configured.equals(type + ":" + param)) return true;
        return false;
    }
}
