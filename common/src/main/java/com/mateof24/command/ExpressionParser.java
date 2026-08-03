package com.mateof24.command;

import com.mateof24.platform.Services;
import com.mateof24.timer.Timer;
import net.minecraft.server.MinecraftServer;

/**
 * The single expression grammar behind both {@link ExpressionEvaluator}
 * (timer durations) and {@link ConditionEvaluator} (start/finish conditions).
 *
 * <p>Everything evaluates to a {@code long}; booleans are 0 and 1. That is what
 * lets one grammar serve both: parentheses, arithmetic and comparisons nest
 * freely, and each surface just interprets the result differently — a duration
 * takes the number, a condition takes {@code != 0}.</p>
 *
 * <p>Before this the two were separate hand-written parsers that duplicated
 * {@code skipSpaces}, number parsing and the whole {@code score(...)} call, and
 * disagreed on what they supported: conditions had no arithmetic and no FTB
 * functions, durations had no {@code time_remaining}. Both now accept the full
 * vocabulary.</p>
 *
 * <pre>
 *   or         := and ( "||" and )*
 *   and        := not ( "&amp;&amp;" not )*
 *   not        := "!" not | comparison
 *   comparison := sum ( (">="|"&lt;="|"=="|"!="|"&gt;"|"&lt;") sum )?
 *   sum        := term ( ("+"|"-") term )*
 *   term       := factor ( ("*"|"/"|"%") factor )*
 *   factor     := "(" or ")" | ("-"|"+") factor | number | variable
 * </pre>
 */
final class ExpressionParser {

    static final int MAX_INPUT_LENGTH = 512;

    private final String input;
    private final MinecraftServer server;
    /** Timer the expression is evaluated against; null when there is none. */
    private final Timer timer;
    private int pos;

    private ExpressionParser(String input, MinecraftServer server, Timer timer) {
        this.input = input.trim();
        this.server = server;
        this.timer = timer;
        this.pos = 0;
    }

    /**
     * Evaluates the whole expression, which must be fully consumed.
     *
     * @param timer context for {@code time_remaining}/{@code time_elapsed};
     *              when null they resolve to 0, matching what the condition
     *              path already did whenever no timer was running.
     * @throws ArithmeticException on division or modulo by zero
     * @throws RuntimeException    on any syntax problem, with a message meant
     *                             for the player
     */
    static long evaluate(String expression, MinecraftServer server, Timer timer) {
        if (expression == null || expression.isBlank()) {
            throw new RuntimeException("expression is empty");
        }
        if (expression.length() > MAX_INPUT_LENGTH) {
            throw new RuntimeException("expression too long (max " + MAX_INPUT_LENGTH + ")");
        }
        ExpressionParser parser = new ExpressionParser(expression, server, timer);
        long result = parser.parseOr();
        parser.skipSpaces();
        if (parser.pos != parser.input.length()) {
            throw new RuntimeException("unexpected character at position " + parser.pos
                    + ": '" + parser.input.charAt(parser.pos) + "'");
        }
        return result;
    }

    // ---- grammar ----

    private long parseOr() {
        long result = parseAnd();
        while (true) {
            skipSpaces();
            if (!peek("||")) break;
            pos += 2;
            long right = parseAnd();
            result = (result != 0 || right != 0) ? 1 : 0;
        }
        return result;
    }

    private long parseAnd() {
        long result = parseNot();
        while (true) {
            skipSpaces();
            if (!peek("&&")) break;
            pos += 2;
            long right = parseNot();
            result = (result != 0 && right != 0) ? 1 : 0;
        }
        return result;
    }

    private long parseNot() {
        skipSpaces();
        // '!' is negation only when it is not the start of '!='.
        if (pos < input.length() && input.charAt(pos) == '!' && !peek("!=")) {
            pos++;
            return parseNot() == 0 ? 1 : 0;
        }
        return parseComparison();
    }

    private long parseComparison() {
        long left = parseSum();
        skipSpaces();
        for (String op : new String[]{">=", "<=", "==", "!=", ">", "<"}) {
            if (!peek(op)) continue;
            pos += op.length();
            long right = parseSum();
            boolean value = switch (op) {
                case ">=" -> left >= right;
                case "<=" -> left <= right;
                case "==" -> left == right;
                case "!=" -> left != right;
                case ">" -> left > right;
                default -> left < right;
            };
            return value ? 1 : 0;
        }
        return left;
    }

    private long parseSum() {
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
            long result = parseOr();
            skipSpaces();
            if (pos >= input.length() || input.charAt(pos) != ')') {
                throw new RuntimeException("missing closing parenthesis at position " + pos);
            }
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
        while (pos < input.length()
                && (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '_')) {
            pos++;
        }
        String name = input.substring(start, pos);
        skipSpaces();
        return switch (name) {
            case "players_online" -> server != null ? server.getPlayerList().getPlayerCount() : 0;
            case "time_remaining" -> timer != null ? timer.getCurrentTicks() / 20L : 0;
            case "time_elapsed" -> timer != null
                    ? Math.max(0, timer.getTargetTicks() - timer.getCurrentTicks()) / 20L
                    : 0;
            case "score" -> parseScoreCall();
            case "ftb_quest_completed" -> parseFtbCall(true);
            case "ftb_reward_claimed" -> parseFtbCall(false);
            default -> throw new RuntimeException("unknown variable '" + name + "'");
        };
    }

    private long parseScoreCall() {
        expect('(', "expected '(' after score");
        skipSpaces();

        int start = pos;
        while (pos < input.length() && input.charAt(pos) != ',' && input.charAt(pos) != ')') pos++;
        String objective = input.substring(start, pos).trim();

        expect(',', "expected ',' in score()");
        skipSpaces();

        start = pos;
        while (pos < input.length() && input.charAt(pos) != ')') pos++;
        String holder = input.substring(start, pos).trim();

        expect(')', "expected ')' in score()");

        if (server == null || objective.isEmpty() || holder.isEmpty()) return 0;
        return Services.PLATFORM.getScoreboardValue(server, objective, holder);
    }

    private long parseFtbCall(boolean quest) {
        expect('(', "expected '(' after ftb function");
        int start = pos;
        while (pos < input.length() && input.charAt(pos) != ')') pos++;
        String hexId = input.substring(start, pos).trim();
        expect(')', "expected ')' in ftb function");

        if (server == null || hexId.isEmpty()) return 0;
        if (!com.mateof24.integration.FTBQuestsIntegration.isInstalled()) return 0;
        boolean result = quest
                ? com.mateof24.integration.FTBQuestsIntegration.isQuestCompletedByAnyPlayer(server, hexId)
                : com.mateof24.integration.FTBQuestsIntegration.isRewardClaimedByAnyPlayer(server, hexId);
        return result ? 1L : 0L;
    }

    // ---- helpers ----

    private void expect(char c, String message) {
        skipSpaces();
        if (pos >= input.length() || input.charAt(pos) != c) throw new RuntimeException(message);
        pos++;
    }

    private boolean peek(String s) {
        return input.startsWith(s, pos);
    }

    private void skipSpaces() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
    }
}
