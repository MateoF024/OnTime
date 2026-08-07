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
 * @param kind      what is being watched
 * @param action    what happens to the timer when it fires
 * @param value     the id, expression or objective the kind needs; empty for
 *                  kinds that watch a bare event
 * @param threshold the score {@link Kind#SCOREBOARD} compares against
 * @param target    the scoreboard holder, {@code *} meaning any player
 */
public record Trigger(Kind kind, Action action, String value, int threshold, String target) {

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
     * pushed by something that happened and waits in {@link TriggerRegistry}
     * to be consumed exactly once, while a polled kind is a question asked on
     * a schedule whose answer can be true for as long as it likes.</p>
     */
    public enum Kind {
        PLAYER_JOIN(false, false),
        PLAYER_LEAVE(false, false),
        PLAYER_DEATH(false, false),
        PLAYER_RESPAWN(false, false),
        DIMENSION_CHANGE(false, true),
        ADVANCEMENT(false, true),
        FTB_QUEST(false, true),
        FTB_REWARD(false, true),
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
        if (target == null || target.isEmpty()) target = "*";
    }

    /** A trigger watching a bare event, or an id-carrying one. */
    public static Trigger of(Kind kind, Action action, String value) {
        return new Trigger(kind, action, value, 0, "*");
    }

    public static Trigger scoreboard(Action action, String objective, int score, String target) {
        return new Trigger(Kind.SCOREBOARD, action, objective, score, target);
    }

    public static Trigger expression(Action action, String expression) {
        return new Trigger(Kind.EXPRESSION, action, expression, 0, "*");
    }

    /** Whether this trigger is complete enough to ever fire. */
    public boolean isValid() {
        return !kind.needsValue() || !value.isBlank();
    }

    /**
     * Identifies this trigger within its timer.
     *
     * <p>Used to key what has already fired. Two triggers that watch the same
     * thing for the same outcome are the same trigger, so the kind, the value
     * and the action are the whole identity — the threshold and target ride
     * along because a scoreboard trigger is not the same trigger at a
     * different score.</p>
     */
    public String key() {
        return kind.lower() + ":" + value + ":" + threshold + ":" + target + ":" + action.lower();
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("kind", kind.lower());
        json.addProperty("action", action.lower());
        if (!value.isEmpty()) json.addProperty("value", value);
        if (kind == Kind.SCOREBOARD) {
            json.addProperty("threshold", threshold);
            json.addProperty("target", target);
        }
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
        String target = json.has("target") ? json.get("target").getAsString() : "*";
        return new Trigger(kind, action, value, threshold, target);
    }
}
