package com.mateof24.trigger;

import com.mateof24.integration.FTBQuestsIntegration;
import com.mateof24.manager.TimerManager;
import com.mateof24.timer.Timer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watches FTB Quests, which has no event we can subscribe to.
 *
 * <p>Completion is asked for rather than pushed, so this turns the answer into
 * a one-shot fire: a completed quest stays completed, and without the memory
 * below the trigger would fire on every poll for as long as the quest stayed
 * done.</p>
 */
public final class FTBQuestsPoller {

    private static final int POLL_INTERVAL_TICKS = 20;
    private static int counter = 0;

    /** Trigger keys that have already fired. Cleared when the timer's triggers change. */
    private static final Set<String> firedOnce = ConcurrentHashMap.newKeySet();

    private FTBQuestsPoller() {}

    public static void poll(MinecraftServer server) {
        if (server == null) return;
        if (!FTBQuestsIntegration.isInstalled()) return;
        counter++;
        if (counter < POLL_INTERVAL_TICKS) return;
        counter = 0;

        FTBQuestsIntegration.tryInit();
        if (!FTBQuestsIntegration.isReady()) return;

        for (Timer timer : TimerManager.getInstance().timersView()) {
            for (Trigger trigger : timer.triggers()) {
                if (trigger.kind() != Trigger.Kind.FTB_QUEST
                        && trigger.kind() != Trigger.Kind.FTB_REWARD) continue;
                if (!trigger.isValid()) continue;

                // The action gate first: a start trigger on a running timer has
                // nothing to do, and firing it would leave the fire pending
                // until the timer stopped, long after the quest was completed.
                if (trigger.action() == Trigger.Action.FINISH && !timer.isRunning()) continue;
                if (trigger.action() == Trigger.Action.START && timer.isRunning()) continue;

                String key = timer.getName() + " " + trigger.key();
                if (firedOnce.contains(key)) continue;

                if (anyPlayerHas(server, trigger)) {
                    TriggerRegistry.fireFor(timer.getName(), trigger);
                    firedOnce.add(key);
                }
            }
        }
    }

    private static boolean anyPlayerHas(MinecraftServer server, Trigger trigger) {
        boolean quest = trigger.kind() == Trigger.Kind.FTB_QUEST;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean has = quest
                    ? FTBQuestsIntegration.hasPlayerCompletedQuest(player, trigger.value())
                    : FTBQuestsIntegration.hasPlayerClaimedReward(player, trigger.value());
            if (has) return true;
        }
        return false;
    }

    /** Forgets what a timer has already fired. Call when its triggers change. */
    public static void resetFor(String timerName) {
        if (timerName == null || timerName.isEmpty()) return;
        String prefix = timerName + " ";
        firedOnce.removeIf(key -> key.startsWith(prefix));
    }

    public static void resetAll() {
        firedOnce.clear();
    }
}
