package com.mateof24.trigger;

import com.mateof24.manager.TimerManager;
import com.mateof24.timer.Timer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Arms, watches and fires every rule.
 *
 * <p>One place where there were two: the start side used to be checked once a
 * second from a loop of its own and the finish side per running execution,
 * and the two had already drifted — the start side never consulted the
 * external condition registry. They are the same question asked of a different
 * set of rules.</p>
 *
 * <p>A rule is armed only while its action could do anything: a rule that
 * starts a timer while no execution exists, one that ends it while one is in
 * flight, and neither during a cooldown — a repeat that cancelled itself the
 * moment it began waiting would be worse than not repeating.</p>
 */
public final class RuleEngine {

    /** Everything one armed rule remembers. Keyed by timer name and rule id. */
    private static final Map<String, ConditionState> states = new HashMap<>();

    /** Ticks left before a rule that has already come true acts. */
    private static final Map<String, Long> pendingDelay = new HashMap<>();

    /** Rules that have fired and are set to fire only once. */
    private static final Set<String> spent = new HashSet<>();

    private RuleEngine() {}

    private static String keyOf(Timer timer, TriggerRule rule) {
        return timer.getName() + " " + rule.id();
    }

    /**
     * One pass over every rule of every timer.
     *
     * <p>Called every tick. The cost while nothing is configured is one walk
     * of a list that is usually empty.</p>
     */
    public static void tick(MinecraftServer server) {
        if (server == null) return;
        TimerManager manager = TimerManager.getInstance();

        for (Timer timer : manager.timersView()) {
            boolean hasRun = manager.hasRunOf(timer.getName());
            boolean cooling = manager.hasPendingCooldown();

            for (TriggerRule rule : timer.rules()) {
                String key = keyOf(timer, rule);
                if (spent.contains(key)) continue;

                boolean shouldArm = rule.isValid() && !cooling
                        && (rule.action() == Trigger.Action.START ? !hasRun : hasRun);

                ConditionState state = states.get(key);
                if (!shouldArm) {
                    if (state != null) {
                        state.disarm();
                        states.remove(key);
                        pendingDelay.remove(key);
                    }
                    continue;
                }

                if (state == null) {
                    state = new ConditionState();
                    armFresh(server, timer, rule, state);
                    states.put(key, state);
                    // Never fires on the tick it armed: whatever was already
                    // true then is the baseline, not a reason.
                    continue;
                }

                if (pendingDelay.containsKey(key)) {
                    long left = pendingDelay.get(key) - 1;
                    if (left > 0) {
                        pendingDelay.put(key, left);
                        continue;
                    }
                    pendingDelay.remove(key);
                    act(server, timer, rule, key);
                    continue;
                }

                if (!ConditionEngine.isTrue(rule.condition(), state, new ServerProbe(server, timer, state))) {
                    continue;
                }

                if (rule.delayTicks() > 0) {
                    // Committed from here: if the condition lapses while it
                    // waits, the action still happens. The moment it described
                    // did occur.
                    pendingDelay.put(key, rule.delayTicks());
                    continue;
                }
                act(server, timer, rule, key);
            }
        }
    }

    /**
     * Takes the first sample.
     *
     * <p>Whoever already meets a condition is recorded as their first observed
     * answer, not excluded — which is what lets a revoked advancement, or a
     * reset quest, be earned again and count.</p>
     */
    private static void armFresh(MinecraftServer server, Timer timer,
                                 TriggerRule rule, ConditionState state) {
        ServerProbe probe = new ServerProbe(server, timer, state);
        Map<String, Set<UUID>> subjects = new HashMap<>();
        Map<String, Set<UUID>> satisfied = new HashMap<>();

        for (Condition.Watch leaf : rule.condition().leaves()) {
            Set<UUID> subject = probe.subject(leaf);
            subjects.put(leaf.id(), subject);
            Set<UUID> meeting = new HashSet<>();
            for (UUID player : subject) {
                if (probe.holds(leaf, player)) meeting.add(player);
            }
            satisfied.put(leaf.id(), meeting);
        }
        state.arm(rule.condition(), subjects, satisfied);
    }

    private static void act(MinecraftServer server, Timer timer, TriggerRule rule, String key) {
        TimerManager manager = TimerManager.getInstance();
        if (rule.action() == Trigger.Action.START) {
            manager.startTimer(timer.getName());
        } else {
            for (com.mateof24.timer.TimerRun run : manager.findRuns(timer.getName(), null)) {
                manager.endRun(run);
            }
        }
        com.mateof24.network.TimerState.markDirty();

        if (rule.once()) spent.add(key);
        // Disarmed either way. The next arming takes a fresh baseline, so a
        // second round starts from nothing rather than from a condition that
        // was already true when the first one ended.
        ConditionState state = states.remove(key);
        if (state != null) state.disarm();
    }

    /** Called by the dispatcher: something happened to somebody. */
    public static void recordEvent(Timer timer, TriggerRule rule,
                                   Condition.Watch leaf, ServerPlayer player) {
        if (player == null) return;
        ConditionState state = states.get(keyOf(timer, rule));
        if (state != null) state.recordEvent(leaf.id(), player.getUUID());
    }

    /** Forgets everything about one timer, which is what editing it has to do. */
    public static void resetFor(String timerName) {
        if (timerName == null || timerName.isEmpty()) return;
        String prefix = timerName + " ";
        states.keySet().removeIf(key -> key.startsWith(prefix));
        pendingDelay.keySet().removeIf(key -> key.startsWith(prefix));
        spent.removeIf(key -> key.startsWith(prefix));
    }

    /**
     * Everything armed, so a restart does not quietly start the round again.
     *
     * <p>Keyed by timer and rule, the same key the engine uses in memory, so a
     * rule that was edited while the server was down simply finds nothing and
     * arms fresh — which is what editing it is supposed to do anyway.</p>
     */
    public static com.google.gson.JsonObject toJson() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        com.google.gson.JsonObject armed = new com.google.gson.JsonObject();
        states.forEach((key, state) -> armed.add(key, state.toJson()));
        json.add("armed", armed);

        com.google.gson.JsonObject waiting = new com.google.gson.JsonObject();
        pendingDelay.forEach(waiting::addProperty);
        json.add("waiting", waiting);

        com.google.gson.JsonArray done = new com.google.gson.JsonArray();
        for (String key : spent) done.add(key);
        json.add("spent", done);
        return json;
    }

    public static void loadFrom(com.google.gson.JsonObject json) {
        resetAll();
        if (json == null) return;
        if (json.has("armed") && json.get("armed").isJsonObject()) {
            com.google.gson.JsonObject armed = json.getAsJsonObject("armed");
            for (String key : armed.keySet()) {
                states.put(key, ConditionState.fromJson(armed.getAsJsonObject(key)));
            }
        }
        if (json.has("waiting") && json.get("waiting").isJsonObject()) {
            com.google.gson.JsonObject waiting = json.getAsJsonObject("waiting");
            for (String key : waiting.keySet()) {
                pendingDelay.put(key, waiting.get(key).getAsLong());
            }
        }
        if (json.has("spent") && json.get("spent").isJsonArray()) {
            for (com.google.gson.JsonElement element : json.getAsJsonArray("spent")) {
                spent.add(element.getAsString());
            }
        }
    }

    public static void resetAll() {
        states.clear();
        pendingDelay.clear();
        spent.clear();
    }
}
