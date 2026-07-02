package com.mateof24.tick;

import com.mateof24.manager.TimerManager;
import com.mateof24.platform.Services;
import com.mateof24.timer.Timer;
import com.mateof24.storage.TimerLogger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

public class TimerTickHandler {
    private static int syncCounter = 0;
    private static final int SYNC_INTERVAL = 20;
    private static int webPanelTickCounter = 0;
    private static final int WEB_PANEL_TICK_INTERVAL = 4;

    private static long cooldownRemaining = 0L;
    private static boolean inRepeatCooldown = false;
    private static String pendingSequenceTimerName = null;
    private static int startConditionCheckCounter = 0;
    private static final int START_CHECK_INTERVAL = 20;

    // Scoreboard updates only need to happen when the displayed second changes (1 Hz),
    // not every server tick (20 Hz). -1 forces a write on the first tick of a new timer.
    private static long lastBroadcastedScoreboardSecond = -1L;
    private static String lastBroadcastedScoreboardTimer = "";

    public static void cancelCooldown() {
        cooldownRemaining = 0;
        inRepeatCooldown = false;
        pendingSequenceTimerName = null;
        lastBroadcastedScoreboardSecond = -1L;
        lastBroadcastedScoreboardTimer = "";
        com.mateof24.trigger.TriggerRegistry.resetAll();
    }

    public static boolean hasPendingCooldown() {
        return inRepeatCooldown || pendingSequenceTimerName != null;
    }

    public static void tick(MinecraftServer server) {
        com.mateof24.trigger.FTBQuestsPoller.poll(server);
        startConditionCheckCounter++;
        if (startConditionCheckCounter >= START_CHECK_INTERVAL) {
            startConditionCheckCounter = 0;
            checkStartConditions(server);
        }
        if (inRepeatCooldown) {
            if (cooldownRemaining > 0) {
                cooldownRemaining--;
                return;
            }
            inRepeatCooldown = false;
            Optional<Timer> opt = TimerManager.getInstance().getActiveTimer();
            if (opt.isPresent()) {
                Timer t = opt.get();
                t.setRunning(true);
                TimerManager.getInstance().saveActiveTimer();
                syncTimerToClients(server, t);
            }
            return;
        }

        if (pendingSequenceTimerName != null) {
            if (cooldownRemaining > 0) {
                cooldownRemaining--;
                return;
            }
            String next = pendingSequenceTimerName;
            pendingSequenceTimerName = null;
            if (TimerManager.getInstance().hasTimer(next)) {
                TimerManager.getInstance().startTimer(next);
                TimerManager.getInstance().getTimer(next).ifPresent(t -> syncTimerToClients(server, t));
            } else {
                Services.PLATFORM.clearScoreboardTimer(server);
                lastBroadcastedScoreboardSecond = -1L;
                lastBroadcastedScoreboardTimer = "";
                Services.PLATFORM.sendTimerSyncPacket(server, "", 0, 0, false, false, false);
            }
            return;
        }

        Optional<Timer> activeTimerOpt = TimerManager.getInstance().getActiveTimer();
        if (activeTimerOpt.isEmpty()) return;

        Timer activeTimer = activeTimerOpt.get();
        if (!activeTimer.isRunning()) return;

        boolean finished = activeTimer.tick();

        if (!finished && activeTimer.getTriggerType() != null
                && "finish".equals(activeTimer.getTriggerAction())) {
            if (com.mateof24.trigger.TriggerRegistry.consumeFor(activeTimer.getName())) finished = true;
        }

        if (!finished && activeTimer.hasCondition() && "finish".equals(activeTimer.getScoreConditionAction())) {
            finished = checkScoreboardCondition(server, activeTimer);
        }

        if (!finished && activeTimer.getConditionExpression() != null && "finish".equals(activeTimer.getConditionExpressionAction())) {
            finished = com.mateof24.command.ConditionEvaluator
                    .evaluate(activeTimer.getConditionExpression(), server, activeTimer)
                    .orElse(false);
        }

        if (!finished && com.mateof24.event.TimerConditionRegistry.hasCondition(activeTimer.getName())) {
            finished = com.mateof24.event.TimerConditionRegistry.evaluate(activeTimer.getName());
        }

        syncCounter++;
        if (syncCounter >= SYNC_INTERVAL) {
            syncCounter = 0;
            syncTimerToClients(server, activeTimer);
        }

        long currentSecond = activeTimer.getCurrentTicks() / 20L;
        if (currentSecond != lastBroadcastedScoreboardSecond
                || !activeTimer.getName().equals(lastBroadcastedScoreboardTimer)) {
            Services.PLATFORM.updateScoreboardTimer(server,
                    activeTimer.getName(),
                    currentSecond,
                    activeTimer.getTargetTicks() / 20L);
            lastBroadcastedScoreboardSecond = currentSecond;
            lastBroadcastedScoreboardTimer = activeTimer.getName();
        }

        if (syncCounter == 0) {
            com.mateof24.event.TimerEventBus.fireOnTick(toInfo(activeTimer));
        }

        if (!finished) {
            webPanelTickCounter++;
            if (webPanelTickCounter >= WEB_PANEL_TICK_INTERVAL) {
                webPanelTickCounter = 0;
                com.mateof24.webpanel.TimerWebPanel.getInstance().onServerTick(activeTimer);
            }
        }

        if (finished) {
            TimerLogger.logFinish(activeTimer);
            com.mateof24.event.TimerEventBus.fireOnFinish(toInfo(activeTimer));
            executeTimerCommand(server, activeTimer);

            if (activeTimer.shouldRepeatAgain()) {
                activeTimer.incrementRepeatsDone();
                activeTimer.reset();
                long cd = activeTimer.getRepeatCooldownTicks();
                if (cd > 0) {
                    TimerManager.getInstance().saveActiveTimer();
                    cooldownRemaining = cd;
                    inRepeatCooldown = true;
                    syncTimerToClients(server, activeTimer);
                } else {
                    activeTimer.setRunning(true);
                    TimerManager.getInstance().saveActiveTimer();
                    syncTimerToClients(server, activeTimer);
                }
            } else {
                String nextTimerName = activeTimer.getNextTimer();
                long seqCd = activeTimer.getSequenceCooldownTicks();
                activeTimer.resetRepeatsDone();
                activeTimer.reset();
                Timer finishedTimer = activeTimer;
                TimerManager.getInstance().clearActiveTimer();
                TimerManager.getInstance().saveTimer(finishedTimer);

                if (nextTimerName != null && TimerManager.getInstance().hasTimer(nextTimerName)) {
                    if (seqCd > 0) {
                        pendingSequenceTimerName = nextTimerName;
                        cooldownRemaining = seqCd;
                        Services.PLATFORM.sendTimerSyncPacket(server, "", 0, 0, false, false, false);
                    } else {
                        TimerManager.getInstance().startTimer(nextTimerName);
                        TimerManager.getInstance().getTimer(nextTimerName).ifPresent(next ->
                                syncTimerToClients(server, next));
                    }
                } else {
                    Services.PLATFORM.clearScoreboardTimer(server);
                    lastBroadcastedScoreboardSecond = -1L;
                    lastBroadcastedScoreboardTimer = "";
                    Services.PLATFORM.sendTimerSyncPacket(server, "", 0, 0, false, false, false);
                }
            }
        }
    }

    private static void checkStartConditions(MinecraftServer server) {
        if (TimerManager.getInstance().getActiveTimer().isPresent()) return;
        if (inRepeatCooldown || pendingSequenceTimerName != null) return;
        for (Timer t : TimerManager.getInstance().getAllTimers().values()) {
            if (t.isRunning()) continue;
            boolean shouldStart = false;
            if (t.getTriggerType() != null && "start".equals(t.getTriggerAction())) {
                if (com.mateof24.trigger.TriggerRegistry.consumeFor(t.getName())) shouldStart = true;
            }
            if (!shouldStart && t.hasCondition() && "start".equals(t.getScoreConditionAction())) {
                shouldStart = checkScoreboardCondition(server, t);
            }
            if (!shouldStart && t.getConditionExpression() != null && "start".equals(t.getConditionExpressionAction())) {
                shouldStart = com.mateof24.command.ConditionEvaluator
                        .evaluate(t.getConditionExpression(), server, t)
                        .orElse(false);
            }
            if (shouldStart) {
                TimerManager.getInstance().startTimer(t.getName());
                TimerManager.getInstance().getTimer(t.getName()).ifPresent(started ->
                        syncTimerToClients(server, started));
                return;
            }
        }
    }

    private static void syncTimerToClients(MinecraftServer server, Timer timer) {
        Services.PLATFORM.sendTimerSyncPacket(server,
                timer.getName(), timer.getCurrentTicks(), timer.getTargetTicks(),
                timer.isCountUp(), timer.isRunning(), timer.isSilent());
    }

    private static void executeTimerCommand(MinecraftServer server, Timer timer) {
        String command = timer.getCommand();
        if (command == null || command.trim().isEmpty()) return;
        String processedCommand = com.mateof24.command.PlaceholderSystem.replacePlaceholders(command, timer);
        try {
            ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
            if (overworld == null) return;
            CommandSourceStack source = com.mateof24.compat.VanillaCompat.createCommandSource(server, overworld, "OnTime");
            server.getCommands().performPrefixedCommand(source, processedCommand);
        } catch (Exception e) {
            com.mateof24.OnTimeConstants.LOGGER.error("Failed to execute timer command: " + processedCommand, e);
        }
    }

    private static boolean checkScoreboardCondition(MinecraftServer server, Timer timer) {
        try {
            return Services.PLATFORM.checkScoreboardCondition(server,
                    timer.getConditionObjective(), timer.getConditionScore(), timer.getConditionTarget());
        } catch (Exception e) {
            com.mateof24.OnTimeConstants.LOGGER.warn("Failed to evaluate scoreboard condition for timer '{}'", timer.getName(), e);
            return false;
        }
    }

    private static com.mateof24.api.TimerInfo toInfo(Timer t) {
        return new com.mateof24.api.TimerInfo(t.getName(), t.getCurrentTicks(), t.getTargetTicks(),
                t.isCountUp(), t.isRunning(), t.isSilent(), t.getCommand(),
                t.isRepeat(), t.getRepeatCount(), t.getRepeatsDone());
    }
}