package com.mateof24.trigger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What one condition has seen, per player, since it was armed.
 *
 * <p>The piece everything else rests on. A condition asks about a
 * <em>change</em>, not about a state: holding an advancement is not the same
 * as earning one. Without this, a timer that ends when somebody earns an
 * advancement would end again the instant it was restarted, because the player
 * still has it.</p>
 *
 * <p>So satisfaction is a rising edge. Each player's last observed answer is
 * kept, and only false-to-true counts. The sample taken when the condition
 * arms is simply the first of those answers, not a fixed list of who to
 * ignore — which is what makes an administrator revoking an advancement, or
 * resetting an FTB quest or reward, behave correctly: the state drops to
 * false, and earning it again is a new transition that does count. A
 * scoreboard falling below its threshold and rising again is the same
 * thing.</p>
 *
 * <p>One of these per condition instance, so the same watch in two groups
 * accumulates twice and independently.</p>
 */
public final class EdgeMemory {

    /** The last answer seen for each player, so a rising edge can be spotted. */
    private final Map<UUID, Boolean> lastSeen = new HashMap<>();

    /** Who has satisfied it since arming, and when — the timestamp is what expires. */
    private final Map<UUID, Long> satisfiedAt = new HashMap<>();

    /**
     * Whoever the condition was watching when it armed.
     *
     * <p>Frozen on purpose: a match should not change its requirements halfway
     * because somebody joined the team. A player who leaves is dropped, or
     * "all of them" could never complete again.</p>
     */
    private final Set<UUID> roster = new HashSet<>();

    private boolean armed;

    /**
     * Starts watching, taking the first sample.
     *
     * @param satisfiedNow who already meets it, which is recorded as their
     *                     last answer rather than as an exclusion list
     */
    public void arm(Set<UUID> subject, Set<UUID> satisfiedNow) {
        lastSeen.clear();
        satisfiedAt.clear();
        roster.clear();
        roster.addAll(subject);
        for (UUID player : subject) lastSeen.put(player, satisfiedNow.contains(player));
        armed = true;
    }

    public void disarm() {
        lastSeen.clear();
        satisfiedAt.clear();
        roster.clear();
        armed = false;
    }

    public boolean isArmed() { return armed; }

    /**
     * Records what this player answers now.
     *
     * <p>A player nobody has seen before — one who joined after arming, or who
     * entered the subject later — has their own first sample taken here rather
     * than inheriting the one from arming. Otherwise a latecomer who already
     * meets the condition would either count immediately or never count.</p>
     *
     * @return true when this was a rising edge, which is the only thing that
     *         counts as satisfying the condition
     */
    public boolean observe(UUID player, boolean satisfiedNow, long now) {
        Boolean previous = lastSeen.put(player, satisfiedNow);
        if (previous == null) return false;
        if (!satisfiedNow || previous) return false;
        satisfiedAt.put(player, now);
        return true;
    }

    /**
     * Whether this player has had a rising edge since the rule armed.
     *
     * <p>What separates "is in the Nether" from "arrived in the Nether":
     * somebody already there when the rule armed has no edge, and so is not a
     * reason for anything to happen.</p>
     */
    public boolean sawEdge(UUID player) {
        return satisfiedAt.containsKey(player);
    }

    /** Drops a player who is no longer being watched, so "all of them" can still finish. */
    public void forget(UUID player) {
        lastSeen.remove(player);
        satisfiedAt.remove(player);
        roster.remove(player);
    }

    /**
     * How many have satisfied it and still count.
     *
     * @param window milliseconds a satisfaction stays good for, or 0 for ever.
     *               This is what makes "both teams ready within thirty seconds"
     *               mean anything: without it a latch from an hour ago still
     *               counts and "both" is satisfied by one.
     */
    public int satisfiedCount(long now, long window) {
        if (window <= 0) return satisfiedAt.size();
        satisfiedAt.values().removeIf(when -> now - when > window);
        return satisfiedAt.size();
    }

    /** The frozen roster, which is what {@code all of them} is counted against. */
    public Set<UUID> roster() { return roster; }

    /** True once nobody is left to satisfy it — an empty subject is never true. */
    public boolean rosterIsEmpty() { return roster.isEmpty(); }

    /**
     * Written out so a match survives a restart.
     *
     * <p>Without it, a server that goes down mid-round comes back having
     * forgotten who had already died, and "all four of them" starts again from
     * nobody — silently, which is the worst way for it to be wrong.</p>
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("armed", armed);
        JsonArray seen = new JsonArray();
        lastSeen.forEach((player, answer) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", player.toString());
            entry.addProperty("held", answer);
            seen.add(entry);
        });
        json.add("lastSeen", seen);

        JsonArray done = new JsonArray();
        satisfiedAt.forEach((player, when) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", player.toString());
            entry.addProperty("at", when);
            done.add(entry);
        });
        json.add("satisfied", done);

        JsonArray who = new JsonArray();
        for (UUID player : roster) who.add(player.toString());
        json.add("roster", who);
        return json;
    }

    public static EdgeMemory fromJson(JsonObject json) {
        EdgeMemory memory = new EdgeMemory();
        if (json == null) return memory;
        memory.armed = json.has("armed") && json.get("armed").getAsBoolean();
        readEach(json, "lastSeen", entry ->
                memory.lastSeen.put(UUID.fromString(entry.get("id").getAsString()),
                        entry.get("held").getAsBoolean()));
        readEach(json, "satisfied", entry ->
                memory.satisfiedAt.put(UUID.fromString(entry.get("id").getAsString()),
                        entry.get("at").getAsLong()));
        if (json.has("roster") && json.get("roster").isJsonArray()) {
            for (com.google.gson.JsonElement element : json.getAsJsonArray("roster")) {
                try {
                    memory.roster.add(UUID.fromString(element.getAsString()));
                } catch (IllegalArgumentException ignored) {
                    // A name that is not a uuid is from a file somebody edited.
                }
            }
        }
        return memory;
    }

    private static void readEach(JsonObject json, String key,
                                 java.util.function.Consumer<JsonObject> each) {
        if (!json.has(key) || !json.get(key).isJsonArray()) return;
        for (com.google.gson.JsonElement element : json.getAsJsonArray(key)) {
            if (!element.isJsonObject()) continue;
            try {
                each.accept(element.getAsJsonObject());
            } catch (RuntimeException ignored) {
                // One unreadable entry loses one player, not the whole round.
            }
        }
    }
}
