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
        for (Trigger trigger : timer.triggers()) {
            if (trigger.action() != want || !trigger.isValid()) continue;
            // No early exit: a pending event trigger has to be consumed even
            // when an earlier one already answered, or it fires again later
            // for an event that has already been dealt with.
            if (evaluate(server, timer, trigger)) fired = true;
        }
        return fired;
    }

    private static boolean evaluate(MinecraftServer server, Timer timer, Trigger trigger) {
        return switch (trigger.kind()) {
            case SCOREBOARD -> scoreboard(server, timer, trigger);
            case EXPRESSION -> com.mateof24.command.ConditionEvaluator
                    .evaluate(trigger.value(), server, timer)
                    .orElse(false);
            default -> TriggerRegistry.consume(timer.getName(), trigger) != null;
        };
    }

    private static boolean scoreboard(MinecraftServer server, Timer timer, Trigger trigger) {
        try {
            return Services.PLATFORM.checkScoreboardCondition(server,
                    trigger.value(), trigger.threshold(), trigger.target());
        } catch (Exception e) {
            com.mateof24.OnTimeConstants.LOGGER.warn(
                    "Failed to evaluate the scoreboard trigger of timer '{}'", timer.getName(), e);
            return false;
        }
    }
}
