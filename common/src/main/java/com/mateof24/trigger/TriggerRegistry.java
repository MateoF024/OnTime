package com.mateof24.trigger;

import java.util.concurrent.atomic.AtomicBoolean;

public class TriggerRegistry {

    private static final AtomicBoolean triggerFired = new AtomicBoolean(false);

    public static void fire() {
        triggerFired.set(true);
    }

    public static boolean consumeTrigger() {
        return triggerFired.getAndSet(false);
    }

    public static void reset() {
        triggerFired.set(false);
    }
}