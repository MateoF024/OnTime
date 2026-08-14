package com.mateof24.command;

import net.minecraft.server.MinecraftServer;

import java.util.OptionalLong;

/**
 * Resolves a timer duration written as an expression, in seconds.
 *
 * <p>Thin wrapper over {@link ExpressionParser}: the grammar is shared with
 * {@link ConditionEvaluator}, so a duration can now use comparisons and the
 * boolean operators too — {@code 60 + 30 * (players_online > 4)} is valid, and
 * evaluates to 90 with five players online.</p>
 *
 * <p>The result is clamped at 0: a duration can never be negative.</p>
 */
public class ExpressionEvaluator {

    private ExpressionEvaluator() {}

    public static class Result {
        public final OptionalLong value;
        public final String error;

        private Result(OptionalLong value, String error) {
            this.value = value;
            this.error = error;
        }

        public static Result ok(long v) { return new Result(OptionalLong.of(v), null); }
        public static Result fail(String err) { return new Result(OptionalLong.empty(), err); }
    }

    public static OptionalLong evaluate(String expression, MinecraftServer server) {
        return evaluateDetailed(expression, server).value;
    }

    public static Result evaluateDetailed(String expression, MinecraftServer server) {
        try {
            // No timer context here: time_remaining/time_elapsed resolve to 0.
            return Result.ok(Math.max(0, ExpressionParser.evaluate(expression, server, null)));
        } catch (ArithmeticException e) {
            return Result.fail("arithmetic error: " + e.getMessage());
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage() != null ? e.getMessage() : "parse error");
        }
    }
}
