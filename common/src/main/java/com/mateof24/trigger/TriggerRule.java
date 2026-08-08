package com.mateof24.trigger;

import com.google.gson.JsonObject;

/**
 * One reason a timer starts or ends: an action and everything that has to be
 * true for it.
 *
 * <p>Replaces the flat {@link Trigger}, which was an action and exactly one
 * watch and so could only ever mean "any of these". A rule owns a whole
 * {@link Condition} tree, which is what lets two teams in two dimensions be
 * one reason rather than two unrelated ones.</p>
 *
 * @param delayTicks how long to wait after the condition comes true. The
 *                   action still happens if the condition lapses during the
 *                   wait: the moment it described did occur, and a countdown
 *                   that cancelled itself because somebody stepped back out of
 *                   a region would be worse than useless.
 * @param once       true to fire and stay spent until the timer is edited;
 *                   false to arm again after each firing.
 */
public record TriggerRule(String id, Trigger.Action action, Condition condition,
                          long delayTicks, boolean once) {

    public TriggerRule {
        if (id == null || id.isBlank()) id = Condition.freshId();
        if (action == null) action = Trigger.Action.FINISH;
        if (delayTicks < 0) delayTicks = 0;
    }

    public static TriggerRule of(Trigger.Action action, Condition condition) {
        return new TriggerRule(Condition.freshId(), action, condition, 0L, false);
    }

    /** A rule with nothing to watch can never fire, and must not be armed. */
    public boolean isValid() {
        if (condition == null) return false;
        if (condition instanceof Condition.Group group && group.isEmpty()) return false;
        for (Condition.Watch leaf : condition.leaves()) {
            if (!leaf.isValid()) return false;
        }
        return !condition.leaves().isEmpty();
    }

    /**
     * The one leaf, when this rule has exactly one.
     *
     * <p>Every rule has one today: the editors still write a single watch, and
     * the tree is there for the shape R9 is building towards. Everything that
     * used to read a flat trigger reads this, so moving the model did not
     * change what the game does.</p>
     *
     * @return null for a rule with a group, which nothing writes yet
     */
    public Condition.Watch singleLeaf() {
        if (condition instanceof Condition.Watch watch) return watch;
        java.util.List<Condition.Watch> leaves = condition == null
                ? java.util.List.of() : condition.leaves();
        return leaves.size() == 1 ? leaves.get(0) : null;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("action", action.lower());
        if (delayTicks > 0) json.addProperty("delayTicks", delayTicks);
        if (once) json.addProperty("once", true);
        if (condition != null) json.add("condition", condition.toJson());
        return json;
    }

    public static TriggerRule fromJson(JsonObject json) {
        if (json == null) return null;
        Condition condition = json.has("condition") && json.get("condition").isJsonObject()
                ? Condition.fromJson(json.getAsJsonObject("condition")) : null;
        if (condition == null) return null;
        return new TriggerRule(
                json.has("id") ? json.get("id").getAsString() : Condition.freshId(),
                Trigger.Action.parse(json.has("action") ? json.get("action").getAsString() : null),
                condition,
                json.has("delayTicks") ? json.get("delayTicks").getAsLong() : 0L,
                json.has("once") && json.get("once").getAsBoolean());
    }

    /**
     * The flat form, as one rule.
     *
     * <p>Lossless: a timer that held four triggers holds four rules, each with
     * a single watch, and behaves exactly as it did — a list of rules is still
     * "any of these", which is what the flat list meant.</p>
     */
    public static TriggerRule fromFlat(Trigger trigger) {
        if (trigger == null) return null;
        Condition.Watch watch = new Condition.Watch(
                Condition.freshId(), trigger.kind(), trigger.value(), trigger.threshold(),
                trigger.who(), true, false);
        return TriggerRule.of(trigger.action(), watch);
    }
}
