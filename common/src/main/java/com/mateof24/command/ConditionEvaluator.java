package com.mateof24.command;

import com.mateof24.platform.Services;
import com.mateof24.timer.Timer;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;

public class ConditionEvaluator {

    private final String input;
    private int pos;
    private final MinecraftServer server;
    private final Timer activeTimer;

    private static final int PARSE_LIMIT = 512;

    private ConditionEvaluator(String input, MinecraftServer server, Timer activeTimer) {
        this.input = input.trim();
        this.pos = 0;
        this.server = server;
        this.activeTimer = activeTimer;
    }

    public static Optional<Boolean> evaluate(String expression, MinecraftServer server, Timer activeTimer) {
        if (expression == null || expression.isBlank() || expression.length() > PARSE_LIMIT) return Optional.empty();
        try {
            ConditionEvaluator eval = new ConditionEvaluator(expression, server, activeTimer);
            boolean result = eval.parseOr();
            eval.skipSpaces();
            if (eval.pos != eval.input.length()) return Optional.empty();
            return Optional.of(result);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private boolean parseOr() {
        boolean result = parseAnd();
        while (true) {
            skipSpaces();
            if (!peek("||")) break;
            pos += 2;
            boolean right = parseAnd();
            result = result || right;
        }
        return result;
    }

    private boolean parseAnd() {
        boolean result = parseNot();
        while (true) {
            skipSpaces();
            if (!peek("&&")) break;
            pos += 2;
            boolean right = parseNot();
            result = result && right;
        }
        return result;
    }

    private boolean parseNot() {
        skipSpaces();
        if (pos < input.length() && input.charAt(pos) == '!') {
            pos++;
            return !parseNot();
        }
        return parsePrimary();
    }

    private boolean parsePrimary() {
        skipSpaces();
        if (pos >= input.length()) throw new RuntimeException("Unexpected end");

        if (input.charAt(pos) == '(') {
            pos++;
            boolean result = parseOr();
            skipSpaces();
            if (pos >= input.length() || input.charAt(pos) != ')') throw new RuntimeException("Missing )");
            pos++;
            return result;
        }

        if (peekWord("player_in_region")) {
            pos += "player_in_region".length();
            skipSpaces();
            if (pos >= input.length() || input.charAt(pos) != '(') throw new RuntimeException("Expected (");
            pos++;
            String regionId = parseStringArg();
            if (pos >= input.length() || input.charAt(pos) != ')') throw new RuntimeException("Expected )");
            pos++;
            return checkPlayerInRegion(regionId);
        }

        return parseComparison();
    }

    private boolean parseComparison() {
        long left = parseNumericValue();
        skipSpaces();
        String op = parseOperator();
        skipSpaces();
        long right = parseNumericValue();
        return switch (op) {
            case ">=" -> left >= right;
            case "<=" -> left <= right;
            case ">"  -> left > right;
            case "<"  -> left < right;
            case "==" -> left == right;
            case "!=" -> left != right;
            default   -> throw new RuntimeException("Unknown op: " + op);
        };
    }

    private long parseNumericValue() {
        skipSpaces();
        if (pos >= input.length()) throw new RuntimeException("Expected value");

        if (peekWord("score")) {
            pos += "score".length();
            skipSpaces();
            if (pos >= input.length() || input.charAt(pos) != '(') throw new RuntimeException("Expected ( after score");
            pos++;
            skipSpaces();
            int start = pos;
            while (pos < input.length() && input.charAt(pos) != ',' && input.charAt(pos) != ')') pos++;
            String objective = input.substring(start, pos).trim();
            if (pos >= input.length() || input.charAt(pos) != ',') throw new RuntimeException("Expected ,");
            pos++;
            skipSpaces();
            start = pos;
            while (pos < input.length() && input.charAt(pos) != ')') pos++;
            String holder = input.substring(start, pos).trim();
            if (pos >= input.length() || input.charAt(pos) != ')') throw new RuntimeException("Expected )");
            pos++;
            if (server == null || objective.isEmpty() || holder.isEmpty()) return 0;
            return Services.PLATFORM.getScoreboardValue(server, objective, holder);
        }

        if (peekWord("time_remaining")) {
            pos += "time_remaining".length();
            return activeTimer != null ? activeTimer.getCurrentTicks() / 20L : 0;
        }

        if (peekWord("time_elapsed")) {
            pos += "time_elapsed".length();
            if (activeTimer == null) return 0;
            long elapsed = activeTimer.getTargetTicks() - activeTimer.getCurrentTicks();
            return Math.max(0, elapsed) / 20L;
        }

        if (peekWord("players_online")) {
            pos += "players_online".length();
            return server != null ? server.getPlayerList().getPlayerCount() : 0;
        }

        if (Character.isDigit(input.charAt(pos)) || (input.charAt(pos) == '-' && pos + 1 < input.length() && Character.isDigit(input.charAt(pos + 1)))) {
            return parseNumber();
        }

        throw new RuntimeException("Unknown value at pos " + pos);
    }

    private long parseNumber() {
        boolean neg = false;
        if (pos < input.length() && input.charAt(pos) == '-') { neg = true; pos++; }
        int start = pos;
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
        if (start == pos) throw new RuntimeException("Expected digit");
        long v = Long.parseLong(input.substring(start, pos));
        return neg ? -v : v;
    }

    private String parseOperator() {
        skipSpaces();
        for (String op : new String[]{">=", "<=", "==", "!=", ">", "<"}) {
            if (peek(op)) { pos += op.length(); return op; }
        }
        throw new RuntimeException("Expected comparator at pos " + pos);
    }

    private String parseStringArg() {
        skipSpaces();
        int start = pos;
        while (pos < input.length() && input.charAt(pos) != ')' && input.charAt(pos) != ',') pos++;
        return input.substring(start, pos).trim();
    }

    private boolean peekWord(String word) {
        if (pos + word.length() > input.length()) return false;
        if (!input.startsWith(word, pos)) return false;
        int after = pos + word.length();
        if (after >= input.length()) return true;
        char c = input.charAt(after);
        return !Character.isLetterOrDigit(c) && c != '_';
    }

    private boolean peek(String s) {
        return input.startsWith(s, pos);
    }

    private boolean checkPlayerInRegion(String regionId) {
        if (server == null || regionId.isEmpty()) return false;
        if (!com.mateof24.integration.WorldProtectorIntegration.isInstalled()) return false;
        return com.mateof24.integration.WorldProtectorIntegration.isAnyPlayerInRegion(server, regionId);
    }

    private void skipSpaces() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
    }
}