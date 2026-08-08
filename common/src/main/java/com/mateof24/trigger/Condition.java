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

    /** True when this node, or anything under it, goes by that id. */
    static boolean holds(Condition node, String id) {
        if (node == null || id == null) return false;
        if (id.equals(node.id())) return true;
        if (!(node instanceof Group group)) return false;
        for (Condition child : group.children()) {
            if (holds(child, id)) return true;
        }
        return false;
    }

    /**
     * This tree without the node of that id, or null when nothing is left.
     *
     * <p>Lives here rather than beside the operation that calls it because it
     * is the one piece of this that can be run without a server, and it is
     * the piece that was wrong: a group whose last child was pruned came back
     * as null, and the caller only checked for an <em>empty group</em> — so
     * the null went into the parent's child list and the next walk of the tree
     * threw. A group that empties is dropped, at any depth.</p>
     */
    static Condition without(Condition node, String id) {
        if (node == null || id == null) return node;
        if (id.equals(node.id())) return null;
        if (!(node instanceof Group group)) return node;

        List<Condition> kept = new ArrayList<>();
        for (Condition child : group.children()) {
            Condition pruned = without(child, id);
            if (pruned == null) continue;
            if (pruned instanceof Group inner && inner.isEmpty()) continue;
            kept.add(pruned);
        }
        if (kept.isEmpty()) return null;
        return new Group(group.id(), group.mode(), group.count(), kept);
    }

    /**
     * This tree with one condition added beside the node of that id.
     *
     * <p>The id may name a group, and then the condition joins it. It may also
     * name a single watch that is standing in for a group — a rule made of one
     * condition has no group in it at all, but the editor still draws it as
     * one, and the id it offers is the watch's. That watch becomes a group of
     * two rather than the request being refused, which is what used to happen:
     * every branch built from the heading was a bare watch, so nothing could
     * ever be added beside anything.</p>
     */
    static Condition addInto(Condition node, String groupId, Condition added) {
        if (node == null || groupId == null) return null;
        if (groupId.equals(node.id()) && node instanceof Watch watch) {
            return Group.of(Group.Mode.ALL, List.of(watch, added));
        }
        if (!(node instanceof Group group)) return null;
        if (groupId.equals(group.id())) {
            List<Condition> children = new ArrayList<>(group.children());
            children.add(added);
            return new Group(group.id(), group.mode(), group.count(), children);
        }
        List<Condition> children = new ArrayList<>();
        boolean found = false;
        for (Condition child : group.children()) {
            // Watches too, not only groups: one of them may be the branch the
            // editor drew, and it has to be allowed to become a real group.
            Condition grown = addInto(child, groupId, added);
            if (grown != null) found = true;
            children.add(grown == null ? child : grown);
        }
        if (!found) return null;
        return new Group(group.id(), group.mode(), group.count(), children);
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
     *                is not earning one and standing in the Nether when the
     *                rule arms is not arriving there. False means a state that
     *                already holds is enough on its own.
     * @param negated true for "nobody is in the Nether".
     */
    record Watch(String id, Trigger.Kind kind, String value, int threshold, Who who,
                 boolean edge, boolean negated) implements Condition {

        public Watch {
            if (id == null || id.isBlank()) id = freshId();
            if (value == null) value = "";
            if (who == null) who = Who.DEFAULT;
        }

        public static Watch of(Trigger.Kind kind, Who who) {
            return new Watch(freshId(), kind, "", 0, who, true, false);
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
     * <p>"All of these hold" means at the same moment. A group carried a
     * window once, so that two things happening minutes apart could still
     * count as together; nothing ever set it, and what it did set was the
     * worst of the two answers — zero meaning "for ever", which turned every
     * and into "both happened at some point".</p>
     */
    record Group(String id, Mode mode, int count,
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
        }

        public static Group of(Mode mode, List<Condition> children) {
            return new Group(freshId(), mode, 1, children);
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
                    children);
        }
    }
}
