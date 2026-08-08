package com.mateof24.trigger;

import java.util.Set;
import java.util.UUID;

/**
 * Answers whether a condition tree is true, now.
 *
 * <p>Deliberately free of Minecraft types: what it needs from the world
 * arrives through {@link Probe}, so the whole of the semantics — edges,
 * latches, quantifiers, windows, empty subjects — can be exercised without a
 * server. That is the part worth being sure about.</p>
 */
public final class ConditionEngine {

    /** What the world can be asked, on behalf of one leaf. */
    public interface Probe {

        /** Who this leaf is watching right now. */
        Set<UUID> subject(Condition.Watch leaf);

        /** Whether this player meets it at this instant, ignoring history. */
        boolean holds(Condition.Watch leaf, UUID player);

        /** Milliseconds, so windows can be tested without waiting for them. */
        long now();
    }

    private ConditionEngine() {}

    public static boolean isTrue(Condition condition, ConditionState state, Probe probe) {
        if (condition == null) return false;
        if (condition instanceof Condition.Group group) return groupIsTrue(group, state, probe);
        return watchIsTrue((Condition.Watch) condition, state, probe);
    }

    private static boolean groupIsTrue(Condition.Group group, ConditionState state, Probe probe) {
        // "All of nobody" is vacuously true in logic and would fire the instant
        // it was armed. Never what was meant.
        if (group.isEmpty()) return false;

        int satisfied = 0;
        for (Condition child : group.children()) {
            if (isTrue(child, state, probe)) satisfied++;
        }
        return satisfied >= group.required();
    }

    /**
     * One leaf.
     *
     * <p>The order matters. Every player in the subject is observed first,
     * because that is what advances the edge memory — skipping it once a
     * quantifier is already met would leave the others' history stale, and a
     * later round would then see an edge that had already happened.</p>
     */
    private static boolean watchIsTrue(Condition.Watch leaf, ConditionState state, Probe probe) {
        if (!leaf.isValid()) return false;

        EdgeMemory memory = state.of(leaf);
        Set<UUID> subject = new java.util.HashSet<>(probe.subject(leaf));
        // Whoever the event happened to, even if the resolver can no longer
        // see them: leaving the server is exactly such an event, and its
        // subject is the one person who is no longer online to be resolved.
        subject.addAll(state.eventPlayers(leaf.id()));

        // An empty subject can never satisfy anything, for the same reason an
        // empty group cannot.
        if (subject.isEmpty()) return false;

        long now = probe.now();
        int have = 0;
        for (UUID player : subject) {
            boolean holds = probe.holds(leaf, player);
            memory.observe(player, holds, now);
            if (!holds) continue;
            // True now, and it became true after the rule armed. Without the
            // second half a player already standing in the Nether would be a
            // reason to start, which is not what "when a player reaches the
            // Nether" says.
            if (leaf.edge() && !memory.sawEdge(player)) continue;
            have++;
        }

        // Whoever the trigger no longer watches stops being expected, or "all
        // of them" could never complete after somebody logged off.
        for (UUID known : Set.copyOf(memory.roster())) {
            if (!subject.contains(known)) memory.forget(known);
        }
        for (UUID player : subject) memory.roster().add(player);

        int needed = WhoResolver.required(leaf.who(), memory.roster().size());
        boolean met = have >= needed;
        return leaf.negated() != met;
    }
}
