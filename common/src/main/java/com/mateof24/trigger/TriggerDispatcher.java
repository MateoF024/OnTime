package com.mateof24.trigger;

import com.mateof24.manager.TimerManager;
import com.mateof24.timer.Timer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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
     * @param param  the id that narrows the kind — a dimension, an advancement
     *               — or null for kinds that carry none. A trigger with an
     *               empty value accepts any.
     * @param player who did it, when anybody did. Needed for more than
     *               bookkeeping now: it is what decides whether a trigger
     *               watching two named people cares about this at all.
     */
    public static void dispatch(Trigger.Kind kind, String param, ServerPlayer player) {
        if (kind == null) return;
        MinecraftServer server = player == null ? null : player.level().getServer();

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

                if (!watches(server, timer, trigger, player)) continue;

                if (!enough(server, timer, trigger, player)) continue;
                TriggerRegistry.fireFor(timer.getName(), trigger,
                        player == null ? null : player.getUUID());
            }
        }
    }

    /** An empty value means "any", which is what a bare kind has always meant. */
    private static boolean matches(Trigger trigger, String param) {
        if (trigger.value().isEmpty()) return true;
        return trigger.value().equalsIgnoreCase(param);
    }

    /**
     * Whether this trigger is watching the player who did it.
     *
     * <p>Every event used to reach every trigger of its kind, so a trigger
     * meant for two named people fired for anybody at all. An event with
     * nobody behind it still reaches everything, because there is no one to
     * exclude.</p>
     */
    private static boolean watches(MinecraftServer server, Timer timer,
                                   Trigger trigger, ServerPlayer player) {
        if (player == null) return true;
        return WhoResolver.covers(server, timer, trigger.who(), player.getUUID());
    }

    /**
     * For "any", the first player is enough. For "all" and "at least", this
     * one is counted and the answer waits until enough of them have acted.
     */
    private static boolean enough(MinecraftServer server, Timer timer,
                                  Trigger trigger, ServerPlayer player) {
        if (!trigger.who().needsProgress()) return true;
        int subjectSize = WhoResolver.resolve(server, timer, trigger.who()).size();
        int needed = WhoResolver.required(trigger.who(), subjectSize);
        return TriggerProgress.reached(timer.getName(), trigger,
                player == null ? null : player.getUUID(), needed);
    }
}
