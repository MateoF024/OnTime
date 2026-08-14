package com.mateof24.trigger;

import com.google.gson.JsonObject;

import java.util.Locale;

/**
 * One reason a timer starts or ends, other than its own clock.
 *
 * <p>There used to be three of these and they were not called the same thing:
 * a game event ({@code triggerType} plus {@code triggerAction}), a scoreboard
 * comparison ({@code conditionObjective}, {@code conditionScore},
 * {@code conditionTarget} plus {@code scoreConditionAction}), and an
 * expression ({@code conditionExpression} plus
 * {@code conditionExpressionAction}). Three sets of fields, three actions,
 * three near-identical blocks in the tick, two command subtrees with different
 * grammars, and a timer could hold exactly one of each and no more.</p>
 *
 * <p>They are one thing: something happens, and the timer starts or finishes.
 * A timer now holds a list of these, so it can have two reasons to start, or a
 * scoreboard check and a game event both ending it, which none of the three
 * could express.</p>
 *
 * <p>Each also says <em>who</em> it watches and <em>how many</em> of them it
 * takes — see {@link Who}. Before that, an event fired for whoever caused it
 * and there was no way to ask for two named players, a team, or the whole
 * server.</p>
 *
 * @param kind      what is being watched
 * @param action    what happens to the timer when it fires
 * @param value     the id, expression or objective the kind needs; empty for
 *                  kinds that watch a bare event
 * @param threshold the score {@link Kind#SCOREBOARD} compares against
 * @param who       whose behaviour counts, and how many of them it takes
 */
public record Trigger(Kind kind, Action action, String value, int threshold, Who who) {

    /** What a trigger does to its timer. */
    public enum Action {
        START, FINISH;

        public static Action parse(String raw) {
            if (raw == null) return FINISH;
            return "start".equalsIgnoreCase(raw.trim()) ? START : FINISH;
        }

        public String lower() { return name().toLowerCase(Locale.ROOT); }
    }

    /**
     * What is watched.
     *
     * <p>{@link #polled} separates the two ways a trigger can come true, which
     * is the distinction the old code kept re-deriving: an event kind is
     * pushed by something that happened and waits in the rule's own inbox to
     * be read exactly once, while a polled kind is a question asked on a
     * schedule whose answer can be true for as long as it likes.</p>
     */
    public enum Kind {
        // Being online is a state, and the edge of it is joining.
        PLAYER_JOIN(true, false),
        // Leaving, dying and coming back are the three that leave nothing
        // behind to ask about. They are true for the one pass that reads them.
        PLAYER_LEAVE(false, false),
        PLAYER_DEATH(false, false),
        PLAYER_RESPAWN(false, false),
        // Being in a dimension is a state, and the edge of it is arriving.
        // As an event it was true for ever after, so "in the Nether and in the
        // End" meant "went to both at some point" -- which one player can do,
        // and which is not what an "and" says.
        DIMENSION_CHANGE(true, true),
        // These three were already asked rather than pushed: the probe queries
        // them and always did. Only the label said otherwise, and the label is
        // what decided they latched.
        ADVANCEMENT(true, true),
        FTB_QUEST(true, true),
        FTB_REWARD(true, true),
        SCOREBOARD(true, true),
        EXPRESSION(true, true);

        private final boolean polled;
        private final boolean needsValue;

        Kind(boolean polled, boolean needsValue) {
            this.polled = polled;
            this.needsValue = needsValue;
        }

        /** True when the answer is asked for, rather than pushed by an event. */
        public boolean polled() { return polled; }

        /** True when the kind is meaningless without {@link Trigger#value()}. */
        public boolean needsValue() { return needsValue; }

        public String lower() { return name().toLowerCase(Locale.ROOT); }

        public static Kind parse(String raw) {
            if (raw == null) return null;
            String cleaned = raw.trim().toUpperCase(Locale.ROOT);
            for (Kind kind : values()) if (kind.name().equals(cleaned)) return kind;
            return null;
        }
    }

    public Trigger {
        if (kind == null) throw new IllegalArgumentException("A trigger needs a kind");
        if (action == null) action = Action.FINISH;
        if (value == null) value = "";
        if (who == null) who = Who.DEFAULT;
    }

    /** A trigger watching a bare event, or an id-carrying one. */
    public static Trigger of(Kind kind, Action action, String value) {
        return new Trigger(kind, action, value, 0, Who.DEFAULT);
    }

    public static Trigger of(Kind kind, Action action, String value, Who who) {
        return new Trigger(kind, action, value, 0, who);
    }

    public static Trigger scoreboard(Action action, String objective, int score, Who who) {
        return new Trigger(Kind.SCOREBOARD, action, objective, score, who);
    }

    public static Trigger expression(Action action, String expression) {
        return new Trigger(Kind.EXPRESSION, action, expression, 0, Who.DEFAULT);
    }

    /** Whether this trigger is complete enough to ever fire. */
    public boolean isValid() {
        if (kind.needsValue() && value.isBlank()) return false;
        return who.isValid();
    }

    /**
     * Identifies this trigger within its timer.
     *
     * <p>Used to key what has already fired. Two triggers that watch the same
     * thing, for the same outcome, over the same players are the same trigger,
     * so all of it counts: the same event over two different teams is two
     * triggers and has to be remembered as two.</p>
     */
    public String key() {
        return kind.lower() + ":" + value + ":" + threshold + ":" + action.lower() + ":" + who.key();
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("kind", kind.lower());
        json.addProperty("action", action.lower());
        if (!value.isEmpty()) json.addProperty("value", value);
        if (kind == Kind.SCOREBOARD) json.addProperty("threshold", threshold);
        json.add("who", who.toJson());
        return json;
    }

    /** Null when the object names no kind this version knows. */
    public static Trigger fromJson(JsonObject json) {
        if (json == null || !json.has("kind")) return null;
        Kind kind = Kind.parse(json.get("kind").getAsString());
        if (kind == null) return null;
        Action action = Action.parse(json.has("action") ? json.get("action").getAsString() : null);
        String value = json.has("value") ? json.get("value").getAsString() : "";
        int threshold = json.has("threshold") ? json.get("threshold").getAsInt() : 0;
        // A trigger saved before triggers could name anybody carried a single
        // scoreboard holder. "*" was everybody; a name was that one player.
        Who who = json.has("who")
                ? Who.fromJson(json.getAsJsonObject("who"))
                : fromOldTarget(json.has("target") ? json.get("target").getAsString() : null);
        return new Trigger(kind, action, value, threshold, who);
    }

    private static Who fromOldTarget(String target) {
        if (target == null || target.isBlank() || "*".equals(target)) {
            return new Who(Who.Scope.EVERYONE, "", Who.Quantifier.ANY, 1);
        }
        return new Who(Who.Scope.PLAYERS, target, Who.Quantifier.ANY, 1);
    }
}
