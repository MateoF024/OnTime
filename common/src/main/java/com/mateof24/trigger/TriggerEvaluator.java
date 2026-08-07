package com.mateof24.trigger;

import com.mateof24.platform.Services;
import com.mateof24.timer.Timer;
import net.minecraft.server.MinecraftServer;

/**
 * Asks whether a timer should start or finish for a reason other than its own
 * clock.
 *
 * <p>One place, asked the same way for both outcomes. The tick used to carry
 * two blocks of near-identical code — one walking the start side of three
 * separate systems, one walking the finish side — which is how they drifted:
 * the start side never consulted the external condition registry, and neither
 * side agreed on what to do when a value was left half-filled.</p>
 */
public final class TriggerEvaluator {

    private TriggerEvaluator() {}

    /**
     * Whether any of this timer's triggers wants the given outcome now.
     *
     * <p>An event trigger is consumed when it fires, so it answers true once
     * per event. A polled trigger is a question and answers true for as long
     * as it is true, which is why the caller must only ask when acting on the
     * answer would change something.</p>
     */
    public static boolean fires(MinecraftServer server, Timer timer, Trigger.Action want) {
        boolean fired = false;
        for (TriggerRule rule : timer.rules()) {
            Condition.Watch leaf = rule.singleLeaf();
            if (leaf == null || rule.action() != want || !leaf.isValid()) continue;
            // No early exit: a pending event trigger has to be consumed even
            // when an earlier one already answered, or it fires again later
            // for an event that has already been dealt with.
            if (evaluate(server, timer, rule, leaf)) fired = true;
        }
        return fired;
    }

    private static boolean evaluate(MinecraftServer server, Timer timer,
                                    TriggerRule rule, Condition.Watch leaf) {
        return switch (leaf.kind()) {
            case SCOREBOARD -> scoreboard(server, timer, leaf);
            case EXPRESSION -> com.mateof24.command.ConditionEvaluator
                    .evaluate(leaf.value(), server, timer)
                    .orElse(false);
            default -> TriggerRegistry.consume(timer.getName(), rule) != null;
        };
    }

    /**
     * How many of the watched players meet the score, against how many it takes.
     *
     * <p>Asked per player rather than with a single holder, which is what the
     * old single {@code target} field could express and nothing more: "all of
     * team red have ten kills" is a count over a set, not a question about one
     * name.</p>
     */
    private static boolean scoreboard(MinecraftServer server, Timer timer, Condition.Watch trigger) {
        try {
            java.util.List<net.minecraft.server.level.ServerPlayer> watched =
                    WhoResolver.resolve(server, timer, trigger.who());
            if (watched.isEmpty()) return false;
            int met = 0;
            for (net.minecraft.server.level.ServerPlayer player : watched) {
                if (Services.PLATFORM.checkScoreboardCondition(server, trigger.value(),
                        trigger.threshold(), player.getScoreboardName())) {
                    met++;
                }
            }
            return met >= WhoResolver.required(trigger.who(), watched.size());
        } catch (Exception e) {
            com.mateof24.OnTimeConstants.LOGGER.warn(
                    "Failed to evaluate the scoreboard trigger of timer '{}'", timer.getName(), e);
            return false;
        }
    }
}
