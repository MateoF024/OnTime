package com.mateof24.tick;

import com.mateof24.manager.TimerManager;
import com.mateof24.platform.Services;
import com.mateof24.timer.Timer;
import com.mateof24.timer.TimerRun;
import com.mateof24.storage.TimerLogger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * Drives every {@link TimerRun} once per server tick.
 *
 * <p>All the per-execution bookkeeping — cooldowns, the scheduled-command
 * cursor, the paced command queue, the scoreboard cursor — used to be static
 * fields here, which is the concrete reason a second execution was impossible:
 * two runs would have shared one of each. It lives on the run now. What stays
 * static is genuinely global: the broadcast cadences.</p>
 */
public class TimerTickHandler {
    private static int syncCounter = 0;
    private static final int SYNC_INTERVAL = 20;
    private static int webPanelTickCounter = 0;
    private static final int WEB_PANEL_TICK_INTERVAL = 4;

    private static int startConditionCheckCounter = 0;
    private static final int START_CHECK_INTERVAL = 20;

    /**
     * Clears the pending state of every run. Used by /timer stop and reset.
     *
     * <p>A run waiting out a sequence cooldown is ended rather than revived:
     * its timer already finished and was reset, and 4.0.0 had dropped the
     * active pointer on entering that state. Keeping it registered would leave
     * an idle run behind that rejects the next /timer start.</p>
     */
    public static void cancelCooldown() {
        TimerManager manager = TimerManager.getInstance();
        for (TimerRun run : List.copyOf(manager.runsView())) {
            if (run.isAwaitingSequence()) {
                manager.endRun(run);
            } else {
                run.cancelPending();
            }
        }
        com.mateof24.trigger.TriggerRegistry.resetAll();
    }

    public static boolean hasPendingCooldown() {
        return TimerManager.getInstance().hasPendingCooldown();
    }

    public static void tick(MinecraftServer server) {
        // Coalesced writes: the preference and config setters only mark
        // themselves dirty, so a command touching many players or many config
        // fields produces one file write here instead of one per change.
        com.mateof24.storage.PlayerPreferences.flush();
        com.mateof24.config.ModConfig.getInstance().flush();
        com.mateof24.trigger.FTBQuestsPoller.poll(server);

        startConditionCheckCounter++;
        if (startConditionCheckCounter >= START_CHECK_INTERVAL) {
            startConditionCheckCounter = 0;
            checkStartConditions(server);
        }

        TimerManager manager = TimerManager.getInstance();
        if (manager.runCount() == 0) {
            com.mateof24.network.TimerState.flush(server);
            return;
        }

        // The cadences only advance while something is actually ticking, so a
        // paused timer does not drift the next broadcast, exactly as before.
        boolean anyTicking = manager.runsView().stream()
                .anyMatch(run -> !run.isInCooldown() && run.isRunning());

        boolean syncNow = false;
        boolean webPanelNow = false;
        if (anyTicking) {
            syncCounter++;
            if (syncCounter >= SYNC_INTERVAL) {
                syncCounter = 0;
                syncNow = true;
            }
            webPanelTickCounter++;
            if (webPanelTickCounter >= WEB_PANEL_TICK_INTERVAL) {
                webPanelTickCounter = 0;
                webPanelNow = true;
            }
        }

        // Snapshot: finishing a run can start the next timer of a sequence,
        // which adds and removes entries from the registry.
        for (TimerRun run : List.copyOf(manager.runsView())) {
            tickRun(server, run, syncNow, webPanelNow);
        }

        // One send per tick at most, covering every change made above plus the
        // 1 Hz heartbeat. Redundant sends within a tick collapse into this.
        com.mateof24.network.TimerState.flush(server);
    }

    private static void tickRun(MinecraftServer server, TimerRun run,
                                boolean syncNow, boolean webPanelNow) {
        drainPendingCommands(server, run);

        switch (run.phase()) {
            case REPEAT_COOLDOWN -> {
                if (run.tickCooldown()) return;
                run.endCooldown();
                run.setRunning(true);
                TimerManager.getInstance().saveActiveTimer();
                com.mateof24.network.TimerState.markDirty();
                return;
            }
            case SEQUENCE_COOLDOWN -> {
                if (run.tickCooldown()) return;
                String next = run.pendingSequenceTimer();
                run.endCooldown();
                TimerManager.getInstance().endRun(run);
                if (TimerManager.getInstance().hasTimer(next)) {
                    TimerManager.getInstance().startTimer(next);
                    com.mateof24.network.TimerState.markDirty();
                } else {
                    clearDisplay(server);
                }
                return;
            }
            default -> { }
        }

        if (!run.isRunning()) return;

        Timer timer = run.timer();
        boolean finished = run.tick();

        if (!finished && timer.getTriggerType() != null
                && "finish".equals(timer.getTriggerAction())) {
            if (com.mateof24.trigger.TriggerRegistry.consumeFor(timer.getName())) finished = true;
        }

        if (!finished && timer.hasCondition() && "finish".equals(timer.getScoreConditionAction())) {
            finished = checkScoreboardCondition(server, timer);
        }

        if (!finished && timer.getConditionExpression() != null
                && "finish".equals(timer.getConditionExpressionAction())) {
            finished = com.mateof24.command.ConditionEvaluator
                    .evaluate(timer.getConditionExpression(), server, timer)
                    .orElse(false);
        }

        if (!finished && com.mateof24.event.TimerConditionRegistry.hasCondition(timer.getName())) {
            finished = com.mateof24.event.TimerConditionRegistry.evaluate(timer.getName());
        }

        // Scheduled commands: fire when the displayed second crosses a
        // threshold. A finish tick resets currentTicks (jump away from zero),
        // which the crossing test naturally ignores — the baseline just moves.
        long commandSecond = run.getCurrentTicks() / 20L;
        if (run.lastCommandSecond() < 0) {
            run.setLastCommandSecond(commandSecond);
        } else if (commandSecond != run.lastCommandSecond()) {
            long previousSecond = run.lastCommandSecond();
            run.setLastCommandSecond(commandSecond);
            if (!finished && timer.hasScheduledCommands()) {
                fireScheduledCommands(server, run, previousSecond, commandSecond);
            }
        }

        if (syncNow) {
            com.mateof24.network.TimerState.markDirty();
            com.mateof24.event.TimerEventBus.fireOnTick(toInfo(timer));
        }

        long currentSecond = run.getCurrentTicks() / 20L;
        if (currentSecond != run.lastScoreboardSecond()) {
            Services.PLATFORM.updateScoreboardTimer(server,
                    timer.getName(), currentSecond, run.getTargetTicks() / 20L);
            run.setLastScoreboardSecond(currentSecond);
        }

        if (!finished) {
            if (webPanelNow) {
                com.mateof24.webpanel.TimerWebPanel.getInstance().onServerTick(timer);
            }
            return;
        }

        onRunFinished(server, run, timer);
    }

    private static void onRunFinished(MinecraftServer server, TimerRun run, Timer timer) {
        TimerLogger.logFinish(timer);
        com.mateof24.event.TimerEventBus.fireOnFinish(toInfo(timer));
        executeTimerCommand(server, run);

        if (run.shouldRepeatAgain()) {
            run.incrementRepeatsDone();
            run.reset();
            long cd = timer.getRepeatCooldownTicks();
            if (cd > 0) {
                run.beginRepeatCooldown(cd);
            } else {
                run.setRunning(true);
            }
            TimerManager.getInstance().saveActiveTimer();
            com.mateof24.network.TimerState.markDirty();
            return;
        }

        String nextTimerName = timer.getNextTimer();
        long seqCd = timer.getSequenceCooldownTicks();
        run.resetRepeatsDone();
        run.reset();

        boolean hasNext = nextTimerName != null && TimerManager.getInstance().hasTimer(nextTimerName);

        if (hasNext && seqCd > 0) {
            // The run stays registered to hold the cooldown, but reports itself
            // as awaiting a sequence, so nothing treats it as the active timer.
            run.beginSequenceCooldown(nextTimerName, seqCd);
            TimerManager.getInstance().saveTimer(timer);
            com.mateof24.network.TimerState.markDirty();
            return;
        }

        TimerManager.getInstance().endRun(run);
        TimerManager.getInstance().saveTimer(timer);

        if (hasNext) {
            TimerManager.getInstance().startTimer(nextTimerName);
            com.mateof24.network.TimerState.markDirty();
        } else {
            clearDisplay(server);
        }
    }

    private static void clearDisplay(MinecraftServer server) {
        Services.PLATFORM.clearScoreboardTimer(server);
        com.mateof24.network.TimerState.markDirty();
    }

    /**
     * Starts any timer whose start trigger or start condition is met.
     *
     * <p>Nothing is evaluated while a run already exists, and the loop stops at
     * the first match. Both are deliberate <em>for now</em>: {@link
     * TimerManager#startTimer} still clears the registry, so evaluating past a
     * live run would silently kill it. Lifting this is what actually fixes the
     * long-standing limitation — a timer with a start trigger can never fire
     * while another one runs — and it can only land once concurrent runs are
     * legal, in the selectors phase.</p>
     */
    private static void checkStartConditions(MinecraftServer server) {
        TimerManager manager = TimerManager.getInstance();
        if (manager.runCount() > 0) return;

        for (Timer t : manager.timersView()) {
            if (t.isRunning() || manager.hasRunOf(t.getName())) continue;

            boolean shouldStart = false;
            if (t.getTriggerType() != null && "start".equals(t.getTriggerAction())) {
                if (com.mateof24.trigger.TriggerRegistry.consumeFor(t.getName())) shouldStart = true;
            }
            if (!shouldStart && t.hasCondition() && "start".equals(t.getScoreConditionAction())) {
                shouldStart = checkScoreboardCondition(server, t);
            }
            if (!shouldStart && t.getConditionExpression() != null
                    && "start".equals(t.getConditionExpressionAction())) {
                shouldStart = com.mateof24.command.ConditionEvaluator
                        .evaluate(t.getConditionExpression(), server, t)
                        .orElse(false);
            }
            if (shouldStart) {
                manager.startTimer(t.getName());
                com.mateof24.network.TimerState.markDirty();
                return;
            }
        }
    }


    private static void executeTimerCommand(MinecraftServer server, TimerRun run) {
        java.util.List<String> toRun = new java.util.ArrayList<>();
        String legacy = run.timer().getCommand();
        if (legacy != null && !legacy.trim().isEmpty()) toRun.add(legacy);
        toRun.addAll(run.timer().getFinishCommands());
        runCommandList(server, run, toRun);
    }

    /**
     * Fires every command event whose threshold was crossed between the two
     * displayed seconds (countdown: prev > at >= curr; count-up:
     * prev < at <= curr). Normal ticking crosses at most one boundary, but a
     * laggy catch-up can cross several — they fire in time order.
     */
    private static void fireScheduledCommands(MinecraftServer server, TimerRun run,
                                              long previousSecond, long currentSecond) {
        Timer timer = run.timer();
        java.util.List<Timer.CommandEvent> crossed = new java.util.ArrayList<>();
        for (Timer.CommandEvent event : timer.getCommandEvents()) {
            long at = event.getAtSeconds();
            boolean hit = timer.isCountUp()
                    ? (previousSecond < at && at <= currentSecond)
                    : (previousSecond > at && at >= currentSecond);
            if (hit) crossed.add(event);
        }
        if (crossed.isEmpty()) return;
        // getCommandEvents() is ascending; countdown visits thresholds high-to-low.
        if (!timer.isCountUp()) java.util.Collections.reverse(crossed);
        for (Timer.CommandEvent event : crossed) {
            runCommandList(server, run, event.getCommands());
        }
    }

    /**
     * Runs the commands in order; one failing command does not stop the
     * rest. With config commandDelayTicks > 0 the (placeholder-resolved)
     * commands are queued on the run instead and drained one per delay window.
     */
    private static void runCommandList(MinecraftServer server, TimerRun run, java.util.List<String> commands) {
        if (commands.isEmpty()) return;
        int delayTicks = com.mateof24.config.ModConfig.getInstance().getCommandDelayTicks();
        if (delayTicks > 0) {
            for (String command : commands) {
                run.queueCommand(com.mateof24.command.PlaceholderSystem
                        .replacePlaceholders(command, run.timer()));
            }
            return;
        }
        for (String command : commands) {
            executeResolvedCommand(server,
                    com.mateof24.command.PlaceholderSystem.replacePlaceholders(command, run.timer()));
        }
    }

    /** One queued command per delay window, preserving enqueue order. */
    private static void drainPendingCommands(MinecraftServer server, TimerRun run) {
        if (!run.hasPendingCommands()) return;
        if (run.tickCommandDelay()) return;
        executeResolvedCommand(server, run.pollPendingCommand());
        if (run.hasPendingCommands()) {
            run.setCommandDelay(Math.max(1,
                    com.mateof24.config.ModConfig.getInstance().getCommandDelayTicks()));
        }
    }

    private static void executeResolvedCommand(MinecraftServer server, String processedCommand) {
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
