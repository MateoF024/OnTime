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
            for (TriggerRule rule : timer.rules()) {
                Condition.Watch leaf = rule.singleLeaf();
                if (leaf == null || leaf.kind() != kind || !leaf.isValid()) continue;
                if (!matches(leaf, param)) continue;

                // A trigger that cannot act is not recorded at all, rather than
                // recorded and dropped later: a start trigger on a running
                // timer would otherwise sit pending and fire the moment it
                // stopped, long after the event.
                if (rule.action() == Trigger.Action.FINISH && !timer.isRunning()) continue;
                if (rule.action() == Trigger.Action.START && timer.isRunning()) continue;

                if (!watches(server, timer, leaf, player)) continue;

                // Recorded, not fired. Whether it is enough is the engine's
                // question now: one player dying says nothing about how many
                // the rule was waiting for, and the tick is where the tree is
                // read as a whole.
                RuleEngine.recordEvent(timer, rule, leaf, player);
            }
        }
    }

    /** An empty value means "any", which is what a bare kind has always meant. */
    private static boolean matches(Condition.Watch leaf, String param) {
        if (leaf.value().isEmpty()) return true;
        return leaf.value().equalsIgnoreCase(param);
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
                                   Condition.Watch leaf, ServerPlayer player) {
        if (player == null) return true;
        return WhoResolver.covers(server, timer, leaf.who(), player.getUUID());
    }

}
