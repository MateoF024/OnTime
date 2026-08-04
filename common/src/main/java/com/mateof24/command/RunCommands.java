package com.mateof24.command;

import com.mateof24.manager.TimerManager;
import com.mateof24.timer.Audience;
import com.mateof24.timer.Timer;
import com.mateof24.timer.TimerRun;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The run-scoped half of the command surface: start, pause, resume, stop,
 * reset and audience.
 *
 * <p>These used to live in {@link LifecycleCommands} and all shared one
 * assumption — that there is at most one execution, so "the timer" was always
 * unambiguous. Every one of them now resolves a <em>selection</em> of runs
 * first, from an optional timer name and an optional player selector. With no
 * arguments the selection is every run, which is the natural generalisation of
 * "the only one" and keeps existing command blocks doing what they did.</p>
 */
final class RunCommands {

    private RunCommands() {}

    // ------------------------------------------------------------------
    // start
    // ------------------------------------------------------------------

    /**
     * {@code /timer start <name> [<targets>] [shared|each]}.
     *
     * @param targets null for a global run — the 4.0.0 shape, seen by whoever
     *                connects later. A resolved selector is a fixed set instead,
     *                so a late joiner is deliberately not part of it.
     */
    static int start(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets,
                     TimerRun.Mode mode) {
        String name = StringArgumentType.getString(ctx, "name");
        TimerManager manager = TimerManager.getInstance();

        if (manager.getTimer(name).isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }

        if (targets == null) {
            if (manager.startShared(name, Audience.global()) == null) {
                ctx.getSource().sendFailure(Component.translatable("ontime.command.start.running", name));
                return 0;
            }
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.start.success", name), true);
            com.mateof24.network.TimerState.markDirty();
            return 1;
        }

        Set<UUID> players = uuidsOf(targets);
        if (mode == TimerRun.Mode.EACH) {
            List<TimerRun> created = manager.startEach(name, players);
            if (created.isEmpty()) {
                ctx.getSource().sendFailure(Component.translatable("ontime.command.start.running", name));
                return 0;
            }
            int count = created.size();
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.start.each", name, count), true);
            com.mateof24.network.TimerState.markDirty();
            return count;
        }

        if (manager.startShared(name, Audience.ofPlayers(players)) == null) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.start.running", name));
            return 0;
        }
        int count = players.size();
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.start.shared", name, count), true);
        com.mateof24.network.TimerState.markDirty();
        return 1;
    }

    // ------------------------------------------------------------------
    // pause / resume
    // ------------------------------------------------------------------

    /**
     * @param running {@code null} toggles. Reachable only from the bare
     *                {@code /timer pause}, which is the 4.0.0 form: with a
     *                single paused run it resumes it, exactly as before. Any
     *                argument means the operator was explicit, and an explicit
     *                {@code pause} never resumes.
     */
    static int setRunning(CommandContext<CommandSourceStack> ctx, Boolean running,
                          String name, Collection<ServerPlayer> targets) {
        List<TimerRun> selected = select(ctx, name, targets);
        if (selected == null) return 0;

        // A run inside a cooldown window has nothing to pause or resume: the
        // tick engine skips its clock entirely until the window closes, so
        // flipping the flag would only produce a message that lies.
        selected.removeIf(TimerRun::isInCooldown);
        if (selected.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.pause.none"));
            return 0;
        }

        boolean target = running != null
                ? running
                // Toggle: resume only when there is nothing left running to pause.
                : selected.stream().noneMatch(TimerRun::isRunning);

        List<TimerRun> changed = new ArrayList<>();
        for (TimerRun run : selected) {
            if (run.isRunning() == target) continue;
            run.setRunning(target);
            if (TimerManager.getInstance().isPrimaryRunOf(run)) run.mirrorToTimer();
            changed.add(run);
        }

        if (changed.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable(
                    target ? "ontime.command.resume.already" : "ontime.command.pause.already",
                    selected.get(0).timerName()));
            return 0;
        }

        TimerManager.getInstance().saveTimers();
        for (TimerRun run : changed) {
            if (target) {
                com.mateof24.event.TimerEventBus.fireOnResume(toInfo(run));
            } else {
                com.mateof24.event.TimerEventBus.fireOnPause(toInfo(run));
            }
        }

        report(ctx, changed, target ? "resume" : "pause", targets);
        com.mateof24.network.TimerState.markDirty();
        return changed.size();
    }

    // ------------------------------------------------------------------
    // stop / reset
    // ------------------------------------------------------------------

    /** Ends the selected runs: clock back to the start and the execution gone. */
    static int stop(CommandContext<CommandSourceStack> ctx, String name, Collection<ServerPlayer> targets) {
        List<TimerRun> selected = select(ctx, name, targets);
        if (selected == null) return 0;

        if (selected.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.stop.none"));
            return 0;
        }

        // A run left waiting on a sequence cooldown is the only member of the
        // selection with nothing to show, so it gets its own message when it is
        // all that was there — 4.0.0 reported the cancelled cooldown too.
        boolean onlyCooldowns = selected.stream().allMatch(TimerRun::isAwaitingSequence);

        for (TimerRun run : selected) {
            run.cancelPending();
            run.resetRepeatsDone();
            run.reset();
            if (TimerManager.getInstance().isPrimaryRunOf(run)) run.mirrorToTimer();
            com.mateof24.trigger.TriggerRegistry.resetFor(run.timerName());
        }
        TimerManager.getInstance().endRuns(selected);
        TimerManager.getInstance().saveTimers();

        if (onlyCooldowns) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.stop.cooldown_cancelled"), true);
        } else {
            report(ctx, selected, "stop", targets);
        }

        com.mateof24.network.TimerState.markDirty();
        return selected.size();
    }

    /** Puts the selected runs back to their starting time, still registered. */
    static int reset(CommandContext<CommandSourceStack> ctx, String name, Collection<ServerPlayer> targets) {
        List<TimerRun> selected = select(ctx, name, targets);
        if (selected == null) return 0;

        if (selected.isEmpty()) {
            // /timer reset <name> on a timer that is not running still resets
            // the definition, which is what 4.0.0 did and what a fresh start
            // after hand-editing the file needs.
            if (name != null && !name.isEmpty() && targets == null) {
                Timer timer = TimerManager.getInstance().getTimer(name).orElse(null);
                if (timer != null) {
                    timer.reset();
                    TimerManager.getInstance().saveTimer(timer);
                    ctx.getSource().sendSuccess(() ->
                            Component.translatable("ontime.command.reset.success", name), true);
                    return 1;
                }
            }
            ctx.getSource().sendFailure(Component.translatable("ontime.command.reset.noactive"));
            return 0;
        }

        for (TimerRun run : selected) {
            com.mateof24.trigger.TriggerRegistry.resetFor(run.timerName());
            // Its timer already finished and was reset; the run only existed to
            // hold the cooldown. Clearing the cooldown and keeping it would
            // leave an idle execution behind that rejects the next start.
            if (run.isAwaitingSequence()) {
                TimerManager.getInstance().endRun(run);
                continue;
            }
            run.cancelPending();
            run.reset();
            if (TimerManager.getInstance().isPrimaryRunOf(run)) {
                run.mirrorToTimer();
                run.timer().reset();
            }
        }
        TimerManager.getInstance().saveTimers();

        report(ctx, selected, "reset", targets);
        com.mateof24.network.TimerState.markDirty();
        return selected.size();
    }

    // ------------------------------------------------------------------
    // audience
    // ------------------------------------------------------------------

    /** {@code /timer audience <name> list} — one line per execution. */
    static int audienceList(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        List<TimerRun> runs = TimerManager.getInstance().findRuns(name, null);
        if (runs.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.audience.norun", name));
            return 0;
        }
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.audience.header", name, runs.size()), false);
        for (TimerRun run : runs) {
            ctx.getSource().sendSuccess(() -> Component.translatable("ontime.command.audience.entry",
                    run.shortId(),
                    Component.translatable(run.mode() == TimerRun.Mode.EACH
                            ? "ontime.mode.each" : "ontime.mode.shared"),
                    describe(ctx, run.audience())), false);
        }
        return runs.size();
    }

    /** {@code /timer audience <name> <add|remove> <targets>}. */
    static int audienceEdit(CommandContext<CommandSourceStack> ctx, boolean add,
                            Collection<ServerPlayer> targets) {
        String name = StringArgumentType.getString(ctx, "name");
        TimerManager manager = TimerManager.getInstance();
        List<TimerRun> runs = manager.findRuns(name, null);
        if (runs.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.audience.norun", name));
            return 0;
        }

        Set<UUID> asked = uuidsOf(targets);
        int touched = 0;
        for (TimerRun run : List.copyOf(runs)) {
            if (run.audience().isGlobal()) continue;

            Audience updated;
            if (add) {
                // Only players no execution of this timer reaches yet: two runs
                // of one timer sharing a viewer would put two clocks of the same
                // timer on their screen.
                Set<UUID> free = new LinkedHashSet<>();
                for (UUID player : asked) {
                    if (manager.findOverlapping(name, Audience.ofPlayer(player)) == null) free.add(player);
                }
                if (free.isEmpty()) continue;
                updated = run.audience().withAdded(free);
            } else {
                updated = run.audience().withRemoved(asked);
                if (updated.size() == run.audience().size()) continue;
            }

            run.setAudience(updated);
            touched++;
            // Nobody is left watching it, so there is nothing for it to be.
            if (!updated.isGlobal() && updated.size() == 0) manager.endRun(run);
        }

        if (touched == 0) {
            ctx.getSource().sendFailure(Component.translatable(
                    runs.stream().allMatch(r -> r.audience().isGlobal())
                            ? "ontime.command.audience.global"
                            : "ontime.command.audience.nochange", name));
            return 0;
        }

        manager.saveRuns();
        final int count = touched;
        ctx.getSource().sendSuccess(() -> Component.translatable(
                add ? "ontime.command.audience.added" : "ontime.command.audience.removed",
                asked.size(), name, count), true);
        com.mateof24.network.TimerState.markDirty();
        return touched;
    }

    // ------------------------------------------------------------------
    // selection
    // ------------------------------------------------------------------

    /**
     * The runs a lifecycle subcommand acts on.
     *
     * @param name    null or empty for any timer
     * @param targets null for any audience; otherwise a run matches when it
     *                reaches at least one of them
     * @return null when the arguments themselves were rejected (an unknown
     *         timer name), in which case the failure has already been sent
     */
    private static List<TimerRun> select(CommandContext<CommandSourceStack> ctx, String name,
                                         Collection<ServerPlayer> targets) {
        TimerManager manager = TimerManager.getInstance();
        String timerName = (name == null || name.isEmpty()) ? null : name;

        if (timerName != null && !manager.hasTimer(timerName)) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", timerName));
            return null;
        }

        return manager.findRuns(timerName, targets == null ? null : uuidsOf(targets));
    }

    /**
     * Success message for an operation over a selection.
     *
     * <p>With a single run this is the 4.0.0 wording, name and all. With
     * several it reports the count, and when players were named it says the
     * shared runs it touched reach more than them — so nobody reads "paused for
     * Bob" as "paused only for Bob".</p>
     */
    private static void report(CommandContext<CommandSourceStack> ctx, List<TimerRun> affected,
                               String verb, Collection<ServerPlayer> targets) {
        if (affected.size() == 1) {
            String name = affected.get(0).timerName();
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command." + verb + ".success", name), true);
        } else {
            int count = affected.size();
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command." + verb + ".multi", count), true);
        }

        if (targets != null && affected.stream().anyMatch(run -> run.mode() == TimerRun.Mode.SHARED)) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.selection.shared_note"), false);
        }
    }

    /** Readable form of an audience for chat: "global" or the player names. */
    private static Component describe(CommandContext<CommandSourceStack> ctx, Audience audience) {
        if (audience.isGlobal()) return Component.translatable("ontime.audience.global");

        net.minecraft.server.MinecraftServer server = ctx.getSource().getServer();
        List<String> names = new ArrayList<>();
        for (UUID player : audience.players()) {
            ServerPlayer online = server.getPlayerList().getPlayer(player);
            names.add(online != null ? online.getName().getString()
                    : player.toString().substring(0, 8));
        }
        return Component.literal(String.join(", ", names));
    }

    private static Set<UUID> uuidsOf(Collection<ServerPlayer> players) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (ServerPlayer player : players) ids.add(player.getUUID());
        return ids;
    }

    private static com.mateof24.api.TimerInfo toInfo(TimerRun run) {
        Timer t = run.timer();
        return new com.mateof24.api.TimerInfo(t.getName(), run.getCurrentTicks(), run.getTargetTicks(),
                run.isCountUp(), run.isRunning(), t.isSilent(), t.getCommand(),
                t.isRepeat(), t.getRepeatCount(), run.getRepeatsDone());
    }
}
