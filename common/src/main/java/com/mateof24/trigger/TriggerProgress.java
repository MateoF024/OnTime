package com.mateof24.trigger;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who has already satisfied a trigger that needs more than one player.
 *
 * <p>An event answers for one player and no more: somebody changed dimension.
 * "All of them changed dimension" is not something any single event can say,
 * so it has to be built up — each player who acts is remembered, and the
 * trigger fires when enough of them have.</p>
 *
 * <p>Kept per timer and per trigger, so the same event watched over two
 * different groups accumulates twice and independently.</p>
 *
 * <p>Cleared when the timer starts or stops and when its triggers change. That
 * is what makes a round repeatable: without it, "all four have died" would
 * stay true for ever after the first round, and the second would fire on the
 * first death.</p>
 */
public final class TriggerProgress {

    private static final Map<String, Set<UUID>> done = new ConcurrentHashMap<>();

    private TriggerProgress() {}

    private static String keyOf(String timerName, TriggerRule rule) {
        return timerName + " " + rule.id();
    }

    /**
     * Records that this player has satisfied the trigger.
     *
     * @param needed how many it takes, resolved against the subject right now
     * @return true when that was the one that completed it, which is also when
     *         the tally is dropped so the next round starts from nothing
     */
    public static boolean reached(String timerName, TriggerRule rule, UUID player, int needed) {
        if (timerName == null || rule == null) return false;
        String key = keyOf(timerName, rule);
        Set<UUID> seen = done.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());

        // A player with no identity behind the event cannot be counted twice,
        // so it counts as one more each time rather than as a set member.
        if (player != null) seen.add(player);
        int have = player != null ? seen.size() : seen.size() + 1;

        if (have < Math.max(1, needed)) return false;
        done.remove(key);
        return true;
    }

    /** How many have satisfied it so far, for anything that wants to show progress. */
    public static int countFor(String timerName, TriggerRule rule) {
        Set<UUID> seen = done.get(keyOf(timerName, rule));
        return seen == null ? 0 : seen.size();
    }

    public static void resetFor(String timerName) {
        if (timerName == null || timerName.isEmpty()) return;
        String prefix = timerName + " ";
        done.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public static void resetAll() {
        done.clear();
    }
}
