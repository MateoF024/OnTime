package com.mateof24.trigger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The memory of one trigger while it is armed.
 *
 * <p>One {@link EdgeMemory} per leaf, keyed by the leaf's own id. Two leaves
 * watching the same thing in two different groups keep separate tallies, and a
 * leaf that gets moved keeps its own.</p>
 *
 * <p>Arming and disarming are the only two moments this changes shape, and
 * they are what the thirteen cases hang off: a trigger that starts a timer is
 * armed while no execution exists, one that ends it while an execution is in
 * flight, and neither during a cooldown. Nothing else may clear it — clearing
 * on every tick would mean an AND could never see its two halves.</p>
 */
public final class ConditionState {

    private final Map<String, EdgeMemory> perLeaf = new HashMap<>();

    /** Set once per group id when its window last mattered, for reporting. */
    private final Map<String, Long> groupSatisfiedAt = new HashMap<>();

    /**
     * Players an event has happened to, per leaf, since arming.
     *
     * <p>An event cannot be asked about — nobody can be polled for "did you
     * die" — so the dispatcher writes it here and the engine reads it as
     * though it were a state. That is what lets one mechanism serve both
     * natures: the leaf still goes through the edge memory, so the first tick
     * after it lands is a rising edge and the latch keeps it.</p>
     */
    private final Map<String, Set<UUID>> inbox = new HashMap<>();

    private boolean armed;

    public boolean isArmed() { return armed; }

    /** Called by the dispatcher when something happens to somebody. */
    public void recordEvent(String leafId, UUID player) {
        if (leafId == null || player == null) return;
        inbox.computeIfAbsent(leafId, k -> new java.util.HashSet<>()).add(player);
    }

    /** Whether an event has landed for this player since arming. */
    public boolean hasEvent(String leafId, UUID player) {
        Set<UUID> seen = inbox.get(leafId);
        return seen != null && seen.contains(player);
    }

    /** The memory for one leaf, made on first use. */
    public EdgeMemory of(Condition.Watch leaf) {
        return perLeaf.computeIfAbsent(leaf.id(), k -> new EdgeMemory());
    }

    /**
     * Starts watching.
     *
     * @param subjects   who each leaf is watching right now, by leaf id
     * @param satisfied  who already meets each leaf, by leaf id — recorded as
     *                   their first observed answer rather than as an
     *                   exclusion, which is what lets a revoked advancement be
     *                   earned again
     */
    public void arm(Condition root, Map<String, Set<UUID>> subjects,
                    Map<String, Set<UUID>> satisfied) {
        perLeaf.clear();
        groupSatisfiedAt.clear();
        inbox.clear();
        if (root != null) {
            for (Condition.Watch leaf : root.leaves()) {
                EdgeMemory memory = new EdgeMemory();
                memory.arm(subjects.getOrDefault(leaf.id(), Set.of()),
                        satisfied.getOrDefault(leaf.id(), Set.of()));
                perLeaf.put(leaf.id(), memory);
            }
        }
        armed = true;
    }

    public void disarm() {
        for (EdgeMemory memory : perLeaf.values()) memory.disarm();
        perLeaf.clear();
        groupSatisfiedAt.clear();
        inbox.clear();
        armed = false;
    }

    /** Drops leaves that no longer exist, so editing a trigger does not leak. */
    public void keepOnly(Condition root) {
        if (root == null) {
            perLeaf.clear();
            return;
        }
        Set<String> alive = new java.util.HashSet<>();
        for (Condition.Watch leaf : root.leaves()) alive.add(leaf.id());
        perLeaf.keySet().retainAll(alive);
    }

    void markGroup(String groupId, long now) { groupSatisfiedAt.put(groupId, now); }

    Long groupSatisfiedAt(String groupId) { return groupSatisfiedAt.get(groupId); }
}
