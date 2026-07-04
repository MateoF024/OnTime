package com.mateof24.trigger;

import com.mateof24.integration.FTBQuestsIntegration;
import com.mateof24.manager.TimerManager;
import com.mateof24.timer.Timer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FTBQuestsPoller {

    private static final int POLL_INTERVAL_TICKS = 20;
    private static int counter = 0;

    /** Keys ("timerName|triggerType") that have already fired once. Cleared on trigger change/reset. */
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

        for (Timer t : TimerManager.getInstance().timersView()) {
            String trigger = t.getTriggerType();
            if (trigger == null) continue;

            boolean isQuest  = trigger.startsWith("ftb_quest:quest:");
            boolean isReward = trigger.startsWith("ftb_quest:reward:");
            if (!isQuest && !isReward) continue;

            String key = t.getName() + "|" + trigger;
            if (firedOnce.contains(key)) continue;

            String hexId = trigger.substring(isQuest ? "ftb_quest:quest:".length() : "ftb_quest:reward:".length());
            if (hexId.isEmpty()) continue;

            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                boolean detected = isQuest
                        ? FTBQuestsIntegration.hasPlayerCompletedQuest(p, hexId)
                        : FTBQuestsIntegration.hasPlayerClaimedReward(p, hexId);
                if (detected) {
                    String action = t.getTriggerAction();
                    boolean valid = ("finish".equals(action) && t.isRunning())
                            || ("start".equals(action) && !t.isRunning());
                    if (valid) {
                        TriggerRegistry.fireFor(t.getName());
                        firedOnce.add(key);
                    }
                    break;
                }
            }
        }
    }

    /** Clear the run-once state for a timer (call when trigger config changes / clears). */
    public static void resetFor(String timerName) {
        if (timerName == null || timerName.isEmpty()) return;
        firedOnce.removeIf(k -> k.startsWith(timerName + "|"));
    }

    public static void resetAll() {
        firedOnce.clear();
    }
}
