package com.mateof24.trigger;

import com.mateof24.manager.TimerManager;
import com.mateof24.timer.Timer;

import java.util.UUID;

/**
 * Turns something that happened in the world into pending fires.
 *
 * <p>Called from the platform event hooks, which know nothing about timers.</p>
 */
public final class TriggerDispatcher {

    private TriggerDispatcher() {}

    public static void dispatch(Trigger.Kind kind, String param) {
        dispatch(kind, param, null);
    }

    /**
     * @param param   the id that narrows the kind — a dimension, an
     *                advancement — or null for kinds that carry none. A
     *                trigger with an empty value accepts any.
     * @param causedBy player behind the event, when there is one. Recorded now
     *                 so per-player runs can later tell which execution the
     *                 event belongs to.
     */
    public static void dispatch(Trigger.Kind kind, String param, UUID causedBy) {
        if (kind == null) return;
        for (Timer timer : TimerManager.getInstance().timersView()) {
            for (Trigger trigger : timer.triggers()) {
                if (trigger.kind() != kind || !trigger.isValid()) continue;
                if (!matches(trigger, param)) continue;
                // A trigger that cannot act is not recorded at all, rather than
                // recorded and dropped later: a start trigger on a running
                // timer would otherwise sit pending and fire the moment it
                // stopped, long after the event.
                if (trigger.action() == Trigger.Action.FINISH && !timer.isRunning()) continue;
                if (trigger.action() == Trigger.Action.START && timer.isRunning()) continue;
                TriggerRegistry.fireFor(timer.getName(), trigger, causedBy);
            }
        }
    }

    /** An empty value means "any", which is what a bare kind has always meant. */
    private static boolean matches(Trigger trigger, String param) {
        if (trigger.value().isEmpty()) return true;
        return trigger.value().equalsIgnoreCase(param);
    }
}
