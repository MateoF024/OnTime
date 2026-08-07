package com.mateof24.trigger;

import com.google.gson.JsonObject;

import java.util.Locale;

/**
 * Which players a trigger watches, and how many of them it takes.
 *
 * <p>Two independent questions, kept apart on purpose. A trigger used to
 * answer neither: a game event fired for whoever caused it, and the scoreboard
 * kind carried a single holder that meant "this player, or {@code *} for
 * anybody" — so "when both of them change dimension" and "when the whole team
 * does" could not be said at all.</p>
 *
 * <p>Separating them is what removes the ambiguity. {@code PLAYERS} with
 * {@link Quantifier#ANY} is "either of them"; the same subject with
 * {@link Quantifier#ALL} is "both". Neither reading has to be guessed from
 * context.</p>
 *
 * @param scope     whose behaviour counts
 * @param value     the names, team or selector {@link #scope} needs; empty for
 *                  the two that need none
 * @param quantifier how many of them have to satisfy the trigger
 * @param count     the number {@link Quantifier#AT_LEAST} compares against
 */
public record Who(Scope scope, String value, Quantifier quantifier, int count) {

    /** Whose behaviour the trigger is watching. */
    public enum Scope {
        /**
         * The timer's own audience.
         *
         * <p>The default, and what every trigger did implicitly before there
         * was a choice. A timer running for one team is usually only
         * interested in that team.</p>
         */
        AUDIENCE(false),
        /** Everybody on the server, whether or not they can see the timer. */
        EVERYONE(false),
        /** A list of names, separated by commas. */
        PLAYERS(true),
        /** One scoreboard team, by name. */
        TEAM(true),
        /**
         * A vanilla selector.
         *
         * <p>The escape hatch, and the reason this list does not need to grow
         * every time somebody wants a group we did not think of: tags,
         * distance, game mode and the rest are already a selector's job.</p>
         */
        SELECTOR(true);

        private final boolean needsValue;

        Scope(boolean needsValue) { this.needsValue = needsValue; }

        public boolean needsValue() { return needsValue; }

        public String lower() { return name().toLowerCase(Locale.ROOT); }

        public static Scope parse(String raw) {
            if (raw == null) return AUDIENCE;
            String cleaned = raw.trim().toUpperCase(Locale.ROOT);
            for (Scope scope : values()) if (scope.name().equals(cleaned)) return scope;
            return AUDIENCE;
        }
    }

    /** How many of the watched players it takes. */
    public enum Quantifier {
        /** One is enough, and the first one fires it. */
        ANY,
        /**
         * Every one of them.
         *
         * <p>For an event this cannot be answered by the event itself — one
         * player changing dimension says nothing about the rest — so the
         * evaluator remembers who has already done it and fires when the last
         * one does. See {@link TriggerProgress}.</p>
         */
        ALL,
        /** A count, which is {@link #ALL} without needing to know the size. */
        AT_LEAST;

        public String lower() { return name().toLowerCase(Locale.ROOT); }

        public static Quantifier parse(String raw) {
            if (raw == null) return ANY;
            String cleaned = raw.trim().toUpperCase(Locale.ROOT);
            for (Quantifier quantifier : values()) if (quantifier.name().equals(cleaned)) return quantifier;
            return ANY;
        }
    }

    /** What a trigger watches when nothing else is said. */
    public static final Who DEFAULT = new Who(Scope.AUDIENCE, "", Quantifier.ANY, 1);

    public Who {
        if (scope == null) scope = Scope.AUDIENCE;
        if (quantifier == null) quantifier = Quantifier.ANY;
        if (value == null) value = "";
        if (count < 1) count = 1;
    }

    /** Whether this is complete enough to name anybody. */
    public boolean isValid() {
        return !scope.needsValue() || !value.isBlank();
    }

    /** Part of a trigger's identity: the same watch on two groups is two triggers. */
    public String key() {
        return scope.lower() + ":" + value + ":" + quantifier.lower() + ":" + count;
    }

    /** True when the answer depends on more than one player having acted. */
    public boolean needsProgress() {
        return quantifier != Quantifier.ANY;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("scope", scope.lower());
        if (!value.isEmpty()) json.addProperty("value", value);
        json.addProperty("quantifier", quantifier.lower());
        if (quantifier == Quantifier.AT_LEAST) json.addProperty("count", count);
        return json;
    }

    public static Who fromJson(JsonObject json) {
        if (json == null) return DEFAULT;
        return new Who(
                Scope.parse(json.has("scope") ? json.get("scope").getAsString() : null),
                json.has("value") ? json.get("value").getAsString() : "",
                Quantifier.parse(json.has("quantifier") ? json.get("quantifier").getAsString() : null),
                json.has("count") ? json.get("count").getAsInt() : 1);
    }
}
