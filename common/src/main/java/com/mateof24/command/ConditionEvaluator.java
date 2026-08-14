package com.mateof24.command;

import com.mateof24.timer.Timer;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;

/**
 * Evaluates a start/finish condition against a timer.
 *
 * <p>Thin wrapper over {@link ExpressionParser}: the grammar is shared with
 * {@link ExpressionEvaluator}, so a condition can now use arithmetic and the
 * FTB Quests functions too — {@code time_remaining < players_online * 10} and
 * {@code ftb_quest_completed(1a2b)} are both valid, and neither used to be.</p>
 *
 * <p>The expression is true when it evaluates to anything other than 0, so a
 * bare {@code ftb_quest_completed(1a2b)} works as a condition on its own; a
 * comparison is no longer required. Returns empty on any syntax error, which
 * the callers treat as "condition not met".</p>
 */
public class ConditionEvaluator {

    private ConditionEvaluator() {}

    public static Optional<Boolean> evaluate(String expression, MinecraftServer server, Timer activeTimer) {
        try {
            return Optional.of(ExpressionParser.evaluate(expression, server, activeTimer) != 0);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
