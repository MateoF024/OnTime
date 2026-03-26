package com.mateof24.tick;

import com.mateof24.manager.TimerManager;
import com.mateof24.platform.Services;
import com.mateof24.timer.Timer;
import com.mateof24.storage.TimerLogger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class TimerTickHandler {
    private static int syncCounter = 0;
    private static final int SYNC_INTERVAL = 20;

    private static long cooldownRemaining = 0L;
    private static boolean inRepeatCooldown = false;
    private static String pendingSequenceTimerName = null;

    public static void cancelCooldown() {
        cooldownRemaining = 0;
        inRepeatCooldown = false;
        pendingSequenceTimerName = null;
    }

    public static boolean hasPendingCooldown() {
        return inRepeatCooldown || pendingSequenceTimerName != null;
    }

    public static void tick(MinecraftServer server) {
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
                TimerManager.getInstance().saveTimers();
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
                Services.PLATFORM.sendTimerSyncPacket(server, "", 0, 0, false, false, false);
            }
            return;
        }

        Optional<Timer> activeTimerOpt = TimerManager.getInstance().getActiveTimer();
        if (activeTimerOpt.isEmpty()) return;

        Timer activeTimer = activeTimerOpt.get();
        if (!activeTimer.isRunning()) return;

        boolean finished = activeTimer.tick();

        if (!finished && activeTimer.hasCondition()) {
            finished = checkScoreboardCondition(server, activeTimer);
        }

        if (!finished && com.mateof24.event.TimerConditionRegistry.hasCondition(activeTimer.getName())) {
            finished = com.mateof24.event.TimerConditionRegistry.evaluate(activeTimer.getName());
        }

        syncCounter++;
        if (syncCounter >= SYNC_INTERVAL) {
            syncCounter = 0;
            syncTimerToClients(server, activeTimer);
        }

        Services.PLATFORM.updateScoreboardTimer(server,
                activeTimer.getName(),
                activeTimer.getCurrentTicks() / 20L,
                activeTimer.getTargetTicks() / 20L);

        if (syncCounter == 0) {
            com.mateof24.event.TimerEventBus.fireOnTick(toInfo(activeTimer));
        }

        if (finished) {
            TimerManager.getInstance().reloadCommandsFromDisk();
            TimerLogger.logFinish(activeTimer);
            com.mateof24.event.TimerEventBus.fireOnFinish(toInfo(activeTimer));
            executeTimerCommand(server, activeTimer);

            if (activeTimer.shouldRepeatAgain()) {
                activeTimer.incrementRepeatsDone();
                activeTimer.reset();
                long cd = activeTimer.getRepeatCooldownTicks();
                TimerManager.getInstance().saveTimers();
                if (cd > 0) {
                    cooldownRemaining = cd;
                    inRepeatCooldown = true;
                    syncTimerToClients(server, activeTimer);
                } else {
                    activeTimer.setRunning(true);
                    TimerManager.getInstance().saveTimers();
                    syncTimerToClients(server, activeTimer);
                }
            } else {
                String nextTimerName = activeTimer.getNextTimer();
                long seqCd = activeTimer.getSequenceCooldownTicks();
                activeTimer.resetRepeatsDone();
                activeTimer.reset();
                TimerManager.getInstance().clearActiveTimer();
                TimerManager.getInstance().saveTimers();

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
                    Services.PLATFORM.sendTimerSyncPacket(server, "", 0, 0, false, false, false);
                }
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
            CommandSourceStack source = new CommandSourceStack(server, Vec3.ZERO, Vec2.ZERO, overworld, 4,
                    "OnTime", net.minecraft.network.chat.Component.literal("OnTime"), server, null);
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