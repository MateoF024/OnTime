package com.mateof24.command;

import com.mateof24.platform.Services;
import net.minecraft.server.MinecraftServer;

import java.util.OptionalLong;

public class ExpressionEvaluator {

    private final String input;
    private int pos;
    private final MinecraftServer server;

    private ExpressionEvaluator(String input, MinecraftServer server) {
        this.input = input.trim();
        this.pos = 0;
        this.server = server;
    }

    public static OptionalLong evaluate(String expression, MinecraftServer server) {
        if (expression == null || expression.isBlank()) return OptionalLong.empty();
        try {
            ExpressionEvaluator eval = new ExpressionEvaluator(expression, server);
            long result = eval.parseExpression();
            eval.skipSpaces();
            if (eval.pos != eval.input.length()) return OptionalLong.empty();
            return OptionalLong.of(Math.max(0, result));
        } catch (Exception e) {
            return OptionalLong.empty();
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
            if (c != '*' && c != '/') break;
            pos++;
            long right = parseFactor();
            if (c == '/' && right == 0) throw new ArithmeticException("Division by zero");
            result = c == '*' ? result * right : result / right;
        }
        return result;
    }

    private long parseFactor() {
        skipSpaces();
        if (pos >= input.length()) throw new RuntimeException("Unexpected end of expression");

        if (input.charAt(pos) == '(') {
            pos++;
            long result = parseExpression();
            skipSpaces();
            if (pos >= input.length() || input.charAt(pos) != ')')
                throw new RuntimeException("Missing closing parenthesis");
            pos++;
            return result;
        }

        if (input.charAt(pos) == '-') {
            pos++;
            return -parseFactor();
        }

        if (Character.isDigit(input.charAt(pos))) return parseNumber();

        if (Character.isLetter(input.charAt(pos)) || input.charAt(pos) == '_') return parseVariable();

        throw new RuntimeException("Unexpected character: " + input.charAt(pos));
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
            default -> throw new RuntimeException("Unknown variable: " + name);
        };
    }

    private long parseScoreCall() {
        skipSpaces();
        if (pos >= input.length() || input.charAt(pos) != '(')
            throw new RuntimeException("Expected '(' after score");
        pos++;
        skipSpaces();

        int start = pos;
        while (pos < input.length() && input.charAt(pos) != ',' && input.charAt(pos) != ')') pos++;
        String objective = input.substring(start, pos).trim();

        if (pos >= input.length() || input.charAt(pos) != ',')
            throw new RuntimeException("Expected ',' in score()");
        pos++;
        skipSpaces();

        start = pos;
        while (pos < input.length() && input.charAt(pos) != ')') pos++;
        String holder = input.substring(start, pos).trim();

        if (pos >= input.length() || input.charAt(pos) != ')')
            throw new RuntimeException("Expected ')' in score()");
        pos++;

        if (server == null || objective.isEmpty() || holder.isEmpty()) return 0;
        return Services.PLATFORM.getScoreboardValue(server, objective, holder);
    }

    private void skipSpaces() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
    }
}