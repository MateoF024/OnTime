package com.mateof24.command;

import com.mateof24.platform.Services;
import net.minecraft.server.MinecraftServer;

import java.util.OptionalLong;

public class ExpressionEvaluator {

    private final String input;
    private int pos;
    private final MinecraftServer server;

    private static final int MAX_INPUT_LENGTH = 512;

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

    private ExpressionEvaluator(String input, MinecraftServer server) {
        this.input = input.trim();
        this.pos = 0;
        this.server = server;
    }

    public static OptionalLong evaluate(String expression, MinecraftServer server) {
        return evaluateDetailed(expression, server).value;
    }

    public static Result evaluateDetailed(String expression, MinecraftServer server) {
        if (expression == null || expression.isBlank()) return Result.fail("expression is empty");
        if (expression.length() > MAX_INPUT_LENGTH) return Result.fail("expression too long (max " + MAX_INPUT_LENGTH + ")");
        try {
            ExpressionEvaluator eval = new ExpressionEvaluator(expression, server);
            long result = eval.parseExpression();
            eval.skipSpaces();
            if (eval.pos != eval.input.length()) {
                return Result.fail("unexpected character at position " + eval.pos + ": '" + eval.input.charAt(eval.pos) + "'");
            }
            return Result.ok(Math.max(0, result));
        } catch (ArithmeticException e) {
            return Result.fail("arithmetic error: " + e.getMessage());
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage() != null ? e.getMessage() : "parse error");
        }
    }

    private long parseExpression() {
        long result = parseTerm();
        while (true) {
            skipSpaces();
            if (pos >= input.length()) break;
            char c = input.charAt(pos);
            if (c != '+' && c != '-') break;
            pos++;
            long right = parseTerm();
            result = c == '+' ? result + right : result - right;
        }
        return result;
    }

    private long parseTerm() {
        long result = parseFactor();
        while (true) {
            skipSpaces();
            if (pos >= input.length()) break;
            char c = input.charAt(pos);
            if (c != '*' && c != '/' && c != '%') break;
            pos++;
            long right = parseFactor();
            switch (c) {
                case '*' -> result = result * right;
                case '/' -> {
                    if (right == 0) throw new ArithmeticException("division by zero");
                    result = result / right;
                }
                case '%' -> {
                    if (right == 0) throw new ArithmeticException("modulo by zero");
                    result = result % right;
                }
            }
        }
        return result;
    }

    private long parseFactor() {
        skipSpaces();
        if (pos >= input.length()) throw new RuntimeException("unexpected end of expression");

        char c = input.charAt(pos);

        if (c == '(') {
            pos++;
            long result = parseExpression();
            skipSpaces();
            if (pos >= input.length() || input.charAt(pos) != ')')
                throw new RuntimeException("missing closing parenthesis at position " + pos);
            pos++;
            return result;
        }

        if (c == '-') {
            pos++;
            return -parseFactor();
        }

        if (c == '+') {
            pos++;
            return parseFactor();
        }

        if (Character.isDigit(c)) return parseNumber();

        if (Character.isLetter(c) || c == '_') return parseVariable();

        throw new RuntimeException("unexpected character '" + c + "' at position " + pos);
    }

    private long parseNumber() {
        int start = pos;
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
        return Long.parseLong(input.substring(start, pos));
    }

    private long parseVariable() {
        int start = pos;
        while (pos < input.length() && (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '_')) pos++;
        String name = input.substring(start, pos);
        skipSpaces();
        return switch (name) {
            case "players_online" -> server != null ? server.getPlayerList().getPlayerCount() : 0;
            case "score" -> parseScoreCall();
            case "ftb_quest_completed"  -> parseFtbQuestCall(true);
            case "ftb_reward_claimed"   -> parseFtbQuestCall(false);
            default -> throw new RuntimeException("unknown variable '" + name + "'");
        };
    }

    private long parseScoreCall() {
        skipSpaces();
        if (pos >= input.length() || input.charAt(pos) != '(')
            throw new RuntimeException("expected '(' after score");
        pos++;
        skipSpaces();

        int start = pos;
        while (pos < input.length() && input.charAt(pos) != ',' && input.charAt(pos) != ')') pos++;
        String objective = input.substring(start, pos).trim();

        if (pos >= input.length() || input.charAt(pos) != ',')
            throw new RuntimeException("expected ',' in score()");
        pos++;
        skipSpaces();

        start = pos;
        while (pos < input.length() && input.charAt(pos) != ')') pos++;
        String holder = input.substring(start, pos).trim();

        if (pos >= input.length() || input.charAt(pos) != ')')
            throw new RuntimeException("expected ')' in score()");
        pos++;

        if (server == null || objective.isEmpty() || holder.isEmpty()) return 0;
        return Services.PLATFORM.getScoreboardValue(server, objective, holder);
    }

    private void skipSpaces() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
    }

    private long parseFtbQuestCall(boolean isQuest) {
        skipSpaces();
        if (pos >= input.length() || input.charAt(pos) != '(')
            throw new RuntimeException("expected '(' after ftb function");
        pos++;
        int start = pos;
        while (pos < input.length() && input.charAt(pos) != ')') pos++;
        String hexId = input.substring(start, pos).trim();
        if (pos >= input.length() || input.charAt(pos) != ')')
            throw new RuntimeException("expected ')' in ftb function");
        pos++;
        if (server == null || hexId.isEmpty()) return 0;
        if (!com.mateof24.integration.FTBQuestsIntegration.isInstalled()) return 0;
        boolean result = isQuest
                ? com.mateof24.integration.FTBQuestsIntegration.isQuestCompletedByAnyPlayer(server, hexId)
                : com.mateof24.integration.FTBQuestsIntegration.isRewardClaimedByAnyPlayer(server, hexId);
        return result ? 1L : 0L;
    }
}
