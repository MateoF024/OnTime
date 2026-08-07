package com.mateof24.trigger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What has to be true for a trigger to act.
 *
 * <p>A tree, because "the match starts when red is in the Nether <em>and</em>
 * blue is in the End" is two conditions with two different subjects and
 * neither is worth anything alone. The flat list this replaces could only ever
 * mean "any of these", which is one shape out of many.</p>
 *
 * <p>Every node carries an {@link #id()} that is generated once and persisted.
 * Memory is keyed by it, not by position: a condition that is moved, or one
 * that appears twice with the same settings, has to keep its own tally rather
 * than inherit a neighbour's.</p>
 */
public interface Condition {

    String id();

    JsonObject toJson();

    /** Every leaf underneath this node, in order. Memory is per leaf. */
    default List<Watch> leaves() {
        List<Watch> out = new ArrayList<>();
        collectLeaves(out);
        return out;
    }

    void collectLeaves(List<Watch> into);

    static String freshId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /** Null when the object describes nothing this version understands. */
    static Condition fromJson(JsonObject json) {
        if (json == null || !json.has("node")) return null;
        return "group".equals(json.get("node").getAsString())
                ? Group.fromJson(json) : Watch.fromJson(json);
    }

    // ==================================================================

    /**
     * One thing being watched, of one group of players.
     *
     * <p>The leaf, and what a whole trigger used to be. The two settings that
     * are new are the ones that decide whether it is asking about a change or
     * about a state — see {@link EdgeMemory} for why that distinction is the
     * whole design.</p>
     *
     * @param edge    true when only a change counts, so holding an advancement
     *                is not earning one. False means a state that already
     *                holds when the trigger arms is enough on its own.
     * @param latched true when satisfying it once keeps it satisfied. Required
     *                for anything combined with AND — one player changing
     *                dimension says nothing about the rest, so the halves have
     *                to be remembered to ever be true together.
     * @param negated true for "nobody is in the Nether". Only meaningful while
     *                live: a latched negative freezes true the moment it is,
     *                which is never what anybody means.
     */
    record Watch(String id, Trigger.Kind kind, String value, int threshold, Who who,
                 boolean edge, boolean latched, boolean negated) implements Condition {

        public Watch {
            if (id == null || id.isBlank()) id = freshId();
            if (value == null) value = "";
            if (who == null) who = Who.DEFAULT;
            // A negated latch is a contradiction rather than a preference, so
            // it is refused here instead of being left to every caller.
            if (negated) latched = false;
        }

        public static Watch of(Trigger.Kind kind, Who who) {
            return new Watch(freshId(), kind, "", 0, who, true, !kind.polled(), false);
        }

        /** Whether this is complete enough to ever be true. */
        public boolean isValid() {
            if (kind.needsValue() && value.isBlank()) return false;
            return who.isValid();
        }

        @Override
        public void collectLeaves(List<Watch> into) { into.add(this); }

        @Override
        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("node", "watch");
            json.addProperty("id", id);
            json.addProperty("kind", kind.lower());
            if (!value.isEmpty()) json.addProperty("value", value);
            if (kind == Trigger.Kind.SCOREBOARD) json.addProperty("threshold", threshold);
            json.add("who", who.toJson());
            json.addProperty("edge", edge);
            json.addProperty("latched", latched);
            if (negated) json.addProperty("negated", true);
            return json;
        }

        static Watch fromJson(JsonObject json) {
            Trigger.Kind kind = Trigger.Kind.parse(
                    json.has("kind") ? json.get("kind").getAsString() : null);
            if (kind == null) return null;
            return new Watch(
                    json.has("id") ? json.get("id").getAsString() : freshId(),
                    kind,
                    json.has("value") ? json.get("value").getAsString() : "",
                    json.has("threshold") ? json.get("threshold").getAsInt() : 0,
                    json.has("who") ? Who.fromJson(json.getAsJsonObject("who")) : Who.DEFAULT,
                    !json.has("edge") || json.get("edge").getAsBoolean(),
                    json.has("latched") && json.get("latched").getAsBoolean(),
                    json.has("negated") && json.get("negated").getAsBoolean());
        }
    }

    // ==================================================================

    /**
     * Several conditions, combined.
     *
     * <p>{@link Mode#AT_LEAST} is the general case and the other two are the
     * ends of it — all of them is a count equal to the size, any of them is a
     * count of one — but both are spelled out because that is how people say
     * them.</p>
     *
     * @param windowMillis how long a satisfied child stays counted, or 0 for
     *                     ever. This is what makes "both teams ready within
     *                     thirty seconds" mean anything: without it a latch
     *                     from an hour ago still counts and "both" is
     *                     satisfied by one.
     */
    record Group(String id, Mode mode, int count, long windowMillis,
                 List<Condition> children) implements Condition {

        public enum Mode {
            ALL, ANY, AT_LEAST;

            public String lower() { return name().toLowerCase(Locale.ROOT); }

            public static Mode parse(String raw) {
                if (raw == null) return ALL;
                String cleaned = raw.trim().toUpperCase(Locale.ROOT);
                for (Mode mode : values()) if (mode.name().equals(cleaned)) return mode;
                return ALL;
            }
        }

        public Group {
            if (id == null || id.isBlank()) id = freshId();
            if (mode == null) mode = Mode.ALL;
            if (children == null) children = List.of();
            children = List.copyOf(children);
            if (count < 1) count = 1;
            if (windowMillis < 0) windowMillis = 0;
        }

        public static Group of(Mode mode, List<Condition> children) {
            return new Group(freshId(), mode, 1, 0L, children);
        }

        /** How many children have to be true. */
        public int required() {
            return switch (mode) {
                case ALL -> Math.max(1, children.size());
                case ANY -> 1;
                case AT_LEAST -> Math.max(1, count);
            };
        }

        /**
         * An empty group is false, not true.
         *
         * <p>"All of nobody" is vacuously true in logic and would fire the
         * instant it was armed, which is never what was meant.</p>
         */
        public boolean isEmpty() { return children.isEmpty(); }

        @Override
        public void collectLeaves(List<Watch> into) {
            for (Condition child : children) child.collectLeaves(into);
        }

        @Override
        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("node", "group");
            json.addProperty("id", id);
            json.addProperty("mode", mode.lower());
            if (mode == Mode.AT_LEAST) json.addProperty("count", count);
            if (windowMillis > 0) json.addProperty("windowMillis", windowMillis);
            JsonArray array = new JsonArray();
            for (Condition child : children) array.add(child.toJson());
            json.add("children", array);
            return json;
        }

        static Group fromJson(JsonObject json) {
            List<Condition> children = new ArrayList<>();
            if (json.has("children") && json.get("children").isJsonArray()) {
                for (JsonElement element : json.getAsJsonArray("children")) {
                    if (!element.isJsonObject()) continue;
                    Condition child = Condition.fromJson(element.getAsJsonObject());
                    if (child != null) children.add(child);
                }
            }
            return new Group(
                    json.has("id") ? json.get("id").getAsString() : freshId(),
                    Mode.parse(json.has("mode") ? json.get("mode").getAsString() : null),
                    json.has("count") ? json.get("count").getAsInt() : 1,
                    json.has("windowMillis") ? json.get("windowMillis").getAsLong() : 0L,
                    children);
        }
    }
}
