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
        // The tally of who has already acted goes with it: a round that starts
        // again starts from nobody, or "all four have died" stays true for ever
        // after the first round.
        com.mateof24.trigger.TriggerProgress.resetAll();
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
            com.mateof24.command.PendingConfirmations.sweep();
        }

        // Costs nothing while no panel is open, which is the normal case.
        com.mateof24.admin.AdminSubscriptions.tick(server);

        // Unconditionally, and above every early return below. The web panel
        // serves a snapshot rather than reading the manager per request, so the
        // moment it stops being ticked it starts serving the past. Ticking it
        // only while something was running meant the last run to finish left
        // its final snapshot published forever: the browser kept a card the
        // server had already deleted, which then answered "No such run".
        // Refreshing it costs one guarded call while no panel is listening.
        com.mateof24.webpanel.TimerWebPanel.getInstance().onServerTick(server);

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
        if (anyTicking) {
            syncCounter++;
            if (syncCounter >= SYNC_INTERVAL) {
                syncCounter = 0;
                syncNow = true;
            }
        }

        // Snapshot: finishing a run can start the next timer of a sequence,
        // which adds and removes entries from the registry.
        for (TimerRun run : List.copyOf(manager.runsView())) {
            tickRun(server, run, syncNow);
        }

        // One send per tick at most, covering every change made above plus the
        // 1 Hz heartbeat. Redundant sends within a tick collapse into this.
        com.mateof24.network.TimerState.flush(server);
    }

    private static void tickRun(MinecraftServer server, TimerRun run, boolean syncNow) {
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
        mirror(run);

        // One question for every reason this timer could end early, asked even
        // when the clock already ended it: a pending event trigger has to be
        // consumed either way, or it survives to fire at the next run.
        boolean triggered = com.mateof24.trigger.TriggerEvaluator
                .fires(server, timer, com.mateof24.trigger.Trigger.Action.FINISH);
        if (triggered) finished = true;

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
            com.mateof24.event.TimerEventBus.fireTick(run);
        }

        long currentSecond = run.getCurrentTicks() / 20L;
        if (currentSecond != run.lastScoreboardSecond()) {
            updateScoreboard(server, run, currentSecond);
            run.setLastScoreboardSecond(currentSecond);
        }

        if (!finished) return;

        onRunFinished(server, run, timer);
    }

    private static void onRunFinished(MinecraftServer server, TimerRun run, Timer timer) {
        TimerLogger.logFinish(timer);
        com.mateof24.event.TimerEventBus.fireFinish(run);
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

    /**
     * Keeps the definition's stored clock in step with its primary run, so
     * every existing reader, the timer file and the downgrade hatch stay
     * correct without knowing runs exist.
     */
    private static void mirror(TimerRun run) {
        if (TimerManager.getInstance().isPrimaryRunOf(run)) run.mirrorToTimer();
    }

    /**
     * Publishes the run's clock to the {@code ontime_active} objective.
     *
     * <p>The timer-name holder keeps meaning what it did in 4.0.0 — the
     * primary run's clock — so existing {@code /execute if score} setups are
     * untouched. A run bound to a player <em>adds</em> a holder under that
     * player's name, which is what makes {@code @s} work per player without
     * anyone having to create an objective. Both holders can be live at once;
     * they answer different questions.</p>
     */
    private static void updateScoreboard(MinecraftServer server, TimerRun run, long currentSecond) {
        long targetSecond = run.getTargetTicks() / 20L;
        if (TimerManager.getInstance().isPrimaryRunOf(run)) {
            Services.PLATFORM.updateScoreboardTimer(server, run.timerName(), currentSecond, targetSecond);
        }
        if (run.owner() != null) {
            var player = server.getPlayerList().getPlayer(run.owner());
            if (player != null) {
                Services.PLATFORM.updateScoreboardTimer(server,
                        player.getScoreboardName(), currentSecond, targetSecond);
            }
        }
    }

    private static void clearDisplay(MinecraftServer server) {
        Services.PLATFORM.clearScoreboardTimer(server);
        com.mateof24.network.TimerState.markDirty();
    }

    /**
     * Starts any timer whose start trigger or start condition is met.
     *
     * <p>Up to 4.0.0 this bailed out entirely while anything was running, and
     * stopped at the first match — it had to, because starting a timer cleared
     * the single active pointer, so evaluating past a live run would have
     * silently killed it. That is the long-standing limitation where a timer
     * with a start trigger could never fire while another one ran. Concurrent
     * runs make both guards unnecessary: every eligible timer starts, each in
     * its own run.</p>
     */
    private static void checkStartConditions(MinecraftServer server) {
        TimerManager manager = TimerManager.getInstance();

        for (Timer t : manager.timersView()) {
            if (t.isRunning() || manager.hasRunOf(t.getName())) continue;

            if (com.mateof24.trigger.TriggerEvaluator
                    .fires(server, t, com.mateof24.trigger.Trigger.Action.START)) {
                manager.startTimer(t.getName());
                com.mateof24.network.TimerState.markDirty();
            }
        }
    }


    private static void executeTimerCommand(MinecraftServer server, TimerRun run) {
        runCommandList(server, run, run.timer().getFinishCommands());
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
                        .replacePlaceholders(command, run, server));
            }
            return;
        }
        for (String command : commands) {
            executeResolvedCommand(server, run,
                    com.mateof24.command.PlaceholderSystem.replacePlaceholders(command, run, server));
        }
    }

    /** One queued command per delay window, preserving enqueue order. */
    private static void drainPendingCommands(MinecraftServer server, TimerRun run) {
        if (!run.hasPendingCommands()) return;
        if (run.tickCommandDelay()) return;
        executeResolvedCommand(server, run, run.pollPendingCommand());
        if (run.hasPendingCommands()) {
            run.setCommandDelay(Math.max(1,
                    com.mateof24.config.ModConfig.getInstance().getCommandDelayTicks()));
        }
    }

    /**
     * Runs the command as the run's own player when it has one, so {@code @s}
     * and relative coordinates mean that player. A shared or global run has no
     * owner and keeps the synthetic overworld source.
     */
    private static void executeResolvedCommand(MinecraftServer server, TimerRun run, String processedCommand) {
        try {
            CommandSourceStack source = null;
            if (run != null && run.owner() != null) {
                var player = server.getPlayerList().getPlayer(run.owner());
                if (player != null) {
                    source = com.mateof24.compat.VanillaCompat.createPlayerCommandSource(server, player);
                }
            }
            if (source == null) {
                ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
                if (overworld == null) return;
                source = com.mateof24.compat.VanillaCompat.createCommandSource(server, overworld, "OnTime");
            }
            server.getCommands().performPrefixedCommand(source, processedCommand);
        } catch (Exception e) {
            com.mateof24.OnTimeConstants.LOGGER.error("Failed to execute timer command: " + processedCommand, e);
        }
    }


}
