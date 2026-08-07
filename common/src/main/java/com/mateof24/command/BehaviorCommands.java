package com.mateof24.command;

import com.mateof24.manager.TimerManager;
import com.mateof24.timer.Timer;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * Handlers for the behavior subcommands:
 * command / repeat / sequence / condition / trigger.
 * The command tree itself is registered by {@link TimerCommands}.
 */
final class BehaviorCommands {

    private BehaviorCommands() {}


    // ---- /timer title <name> ... (counter titles, 4.0.0) ----

    /** Maximum raw length of a single title spec. */
    private static final int MAX_TITLE_LENGTH = 256;

    private static final String[] TITLE_POSITIONS = {"above", "below", "left", "right"};

    /** Requests a push when the edited timer is one somebody is looking at. */
    private static void resyncIfActive(CommandContext<CommandSourceStack> ctx, String name) {
        if (TimerManager.getInstance().hasRunOf(name)) {
            com.mateof24.network.TimerState.markDirty();
        }
    }

    static int setTitle(CommandContext<CommandSourceStack> ctx, String position, String text) {
        String name = StringArgumentType.getString(ctx, "name");
        if (!TimerManager.getInstance().hasTimer(name)) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        if (text.length() > MAX_TITLE_LENGTH) {
            ctx.getSource().sendFailure(Component.translatable(
                    "ontime.command.title.too_long", MAX_TITLE_LENGTH));
            return 0;
        }
        // Validate now so a broken JSON spec is rejected instead of stored.
        if (com.mateof24.compat.VanillaCompat.parseTitle(text) == null) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.title.invalid_json"));
            return 0;
        }
        TimerManager.getInstance().setTimerTitle(name, position, text);
        resyncIfActive(ctx, name);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.title.set", position, name, text), true);
        return 1;
    }

    static int clearTitle(CommandContext<CommandSourceStack> ctx, String position) {
        String name = StringArgumentType.getString(ctx, "name");
        if (!TimerManager.getInstance().setTimerTitle(name, position, null)) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        resyncIfActive(ctx, name);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.title.cleared", position, name), true);
        return 1;
    }

    static int clearAllTitles(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (!TimerManager.getInstance().clearTimerTitles(name)) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        resyncIfActive(ctx, name);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.title.cleared_all", name), true);
        return 1;
    }

    static int viewTitles(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        Timer timer = timerOpt.get();
        if (!timer.hasTitles()) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.title.current.none", name), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.title.current.header", name), false);
        for (String position : TITLE_POSITIONS) {
            String raw = timer.getTitle(position);
            if (raw == null) continue;
            final Component line = Component.translatable("ontime.command.title.current.entry", position, raw);
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    // ---- /timer commands <name> ... (scheduled commands, 4.0.0) ----

    private static String formatSeconds(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return hours > 0
                ? String.format("%02d:%02d:%02d", hours, minutes, seconds)
                : String.format("%02d:%02d", minutes, seconds);
    }

    static int addScheduledCommand(CommandContext<CommandSourceStack> ctx,
                                   int hours, int minutes, int seconds, String command) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        com.mateof24.validation.CommandValidator.ValidationResult validation =
                com.mateof24.validation.CommandValidator.validate(command);
        if (!validation.isValid()) {
            ctx.getSource().sendFailure(validation.getErrorMessage());
            return 0;
        }
        long atSeconds = hours * 3600L + minutes * 60L + seconds;
        long targetSeconds = timerOpt.get().getTargetTicks() / 20L;
        if (atSeconds <= 0 || atSeconds >= targetSeconds) {
            ctx.getSource().sendFailure(Component.translatable(
                    "ontime.command.commands.invalid_time", formatSeconds(targetSeconds), name));
            return 0;
        }
        if (!TimerManager.getInstance().addScheduledCommand(name, atSeconds, command)) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.commands.limit",
                    name, Timer.MAX_SCHEDULED_ENTRIES, Timer.MAX_COMMANDS_PER_POINT));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "ontime.command.commands.added", formatSeconds(atSeconds), name, command), true);
        return 1;
    }

    static int addFinishCommand(CommandContext<CommandSourceStack> ctx, String command) {
        String name = StringArgumentType.getString(ctx, "name");
        if (!TimerManager.getInstance().hasTimer(name)) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        com.mateof24.validation.CommandValidator.ValidationResult validation =
                com.mateof24.validation.CommandValidator.validate(command);
        if (!validation.isValid()) {
            ctx.getSource().sendFailure(validation.getErrorMessage());
            return 0;
        }
        if (!TimerManager.getInstance().addFinishCommand(name, command)) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.commands.limit",
                    name, Timer.MAX_SCHEDULED_ENTRIES, Timer.MAX_COMMANDS_PER_POINT));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "ontime.command.commands.added_finish", name, command), true);
        return 1;
    }

    static int listScheduledCommands(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        Timer timer = timerOpt.get();
        java.util.List<Timer.ScheduledEntry> entries = timer.scheduledEntries();
        if (entries.isEmpty()) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.commands.list.empty", name), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.commands.list.header", name), false);
        int index = 1;
        for (Timer.ScheduledEntry entry : entries) {
            final int shownIndex = index++;
            final Component line = entry.atSeconds() == null
                    ? Component.translatable("ontime.command.commands.list.finish", shownIndex, entry.command())
                    : Component.translatable("ontime.command.commands.list.at", shownIndex,
                            formatSeconds(entry.atSeconds()), entry.command());
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    static int removeScheduledCommand(CommandContext<CommandSourceStack> ctx, int index) {
        String name = StringArgumentType.getString(ctx, "name");
        if (!TimerManager.getInstance().hasTimer(name)) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        if (!TimerManager.getInstance().removeScheduledEntry(name, index - 1)) {
            ctx.getSource().sendFailure(Component.translatable(
                    "ontime.command.commands.invalid_index", index, name));
            return 0;
        }
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.commands.removed", index, name), true);
        return 1;
    }

    static int clearScheduledCommands(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (!TimerManager.getInstance().clearScheduledCommands(name)) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.commands.cleared", name), true);
        return 1;
    }

    static int toggleRepeatInfinite(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        Timer timer = timerOpt.get();
        boolean newRepeat = !timer.isRepeat();
        timer.setRepeat(newRepeat);
        if (newRepeat) {
            timer.setRepeatCount(-1);
        } else {
            timer.setRepeatCooldownTicks(0);
        }
        TimerManager.getInstance().saveTimers();
        ctx.getSource().sendSuccess(() -> Component.translatable(
                newRepeat ? "ontime.command.repeat.enabled_infinite"
                        : "ontime.command.repeat.disabled", name), true);
        return 1;
    }

    static int setRepeatCount(CommandContext<CommandSourceStack> ctx, int count, int cooldownSeconds) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        Timer timer = timerOpt.get();
        if (count == 0) {
            timer.setRepeat(false);
            timer.setRepeatCount(0);
            timer.setRepeatCooldownTicks(0);
            TimerManager.getInstance().saveTimers();
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.repeat.disabled", name), true);
        } else if (count == -1) {
            timer.setRepeat(true);
            timer.setRepeatCount(-1);
            timer.setRepeatCooldownTicks(cooldownSeconds * 20L);
            TimerManager.getInstance().saveTimers();
            if (cooldownSeconds > 0) {
                ctx.getSource().sendSuccess(() ->
                        Component.translatable("ontime.command.repeat.enabled_infinite_cooldown", name, cooldownSeconds), true);
            } else {
                ctx.getSource().sendSuccess(() ->
                        Component.translatable("ontime.command.repeat.enabled_infinite", name), true);
            }
        } else {
            timer.setRepeat(true);
            timer.setRepeatCount(count);
            timer.setRepeatCooldownTicks(cooldownSeconds * 20L);
            TimerManager.getInstance().saveTimers();
            if (cooldownSeconds > 0) {
                ctx.getSource().sendSuccess(() ->
                        Component.translatable("ontime.command.repeat.enabled_count_cooldown", name, count, cooldownSeconds), true);
            } else {
                ctx.getSource().sendSuccess(() ->
                        Component.translatable("ontime.command.repeat.enabled_count", name, count), true);
            }
        }
        return 1;
    }

    static int viewSequence(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        Timer timer = timerOpt.get();
        String next = timer.getNextTimer();
        long cdSec = timer.getSequenceCooldownTicks() / 20L;
        if (next == null) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.sequence.current", name, "(none)"), false);
        } else if (cdSec > 0) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.sequence.current_cooldown", name, next, cdSec), false);
        } else {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.sequence.current", name, next), false);
        }
        return 1;
    }

    static int setSequence(CommandContext<CommandSourceStack> ctx, String nextName, int cooldownSeconds) {
        String name = StringArgumentType.getString(ctx, "name");
        if (name.equals(nextName)) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.sequence.self"));
            return 0;
        }
        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        if (!TimerManager.getInstance().hasTimer(nextName)) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", nextName));
            return 0;
        }
        timerOpt.get().setNextTimer(nextName);
        timerOpt.get().setSequenceCooldownTicks(cooldownSeconds * 20L);
        TimerManager.getInstance().saveTimers();
        if (cooldownSeconds > 0) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.sequence.set_cooldown", name, nextName, cooldownSeconds), true);
        } else {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.sequence.set", name, nextName), true);
        }
        return 1;
    }

    static int clearSequence(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        timerOpt.get().setNextTimer(null);
        timerOpt.get().setSequenceCooldownTicks(0);
        TimerManager.getInstance().saveTimers();
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.sequence.cleared", name), true);
        return 1;
    }

    // ---- /timer trigger <name> ... ----

    /**
     * Prints the list, numbered from one, which is how removal addresses it.
     */
    static int listTriggers(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        java.util.List<com.mateof24.trigger.Trigger> triggers = timerOpt.get().triggers();
        if (triggers.isEmpty()) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.trigger.list.empty", name), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.trigger.list.header", name), false);
        int index = 1;
        for (com.mateof24.trigger.Trigger trigger : triggers) {
            final int shown = index++;
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "ontime.command.trigger.list.row", shown, describe(trigger),
                    Component.translatable("ontime.trigger.action." + trigger.action().lower())), false);
        }
        return 1;
    }

    /** {@code <kind> <value>}, or just the kind when it watches a bare event. */
    static Component describe(com.mateof24.trigger.Trigger trigger) {
        Component kind = Component.translatable("ontime.trigger.kind." + trigger.kind().lower());
        if (trigger.kind() == com.mateof24.trigger.Trigger.Kind.SCOREBOARD) {
            return Component.translatable("ontime.command.trigger.describe.scoreboard",
                    kind, trigger.value(), trigger.threshold(), describeWho(trigger.who()));
        }
        Component who = describeWho(trigger.who());
        if (trigger.value().isEmpty()) {
            return Component.translatable("ontime.command.trigger.describe.who", kind, who);
        }
        return Component.translatable("ontime.command.trigger.describe.value",
                kind, trigger.value(), who);
    }

    /** "any of Bob, Ann" / "all of team red" / "at least 3 of anybody". */
    static Component describeWho(com.mateof24.trigger.Who who) {
        Component scope = who.scope().needsValue()
                ? Component.translatable("ontime.who.scope." + who.scope().lower(), who.value())
                : Component.translatable("ontime.who.scope." + who.scope().lower());
        if (who.quantifier() == com.mateof24.trigger.Who.Quantifier.AT_LEAST) {
            return Component.translatable("ontime.who.at_least", who.count(), scope);
        }
        return Component.translatable("ontime.who." + who.quantifier().lower(), scope);
    }

    static int addTrigger(CommandContext<CommandSourceStack> ctx, com.mateof24.trigger.Trigger trigger) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        if (!timerOpt.get().addTrigger(trigger)) {
            ctx.getSource().sendFailure(Component.translatable(
                    "ontime.command.trigger.rejected", name, Timer.MAX_TRIGGERS));
            return 0;
        }
        forget(name);
        TimerManager.getInstance().saveTimers();
        ctx.getSource().sendSuccess(() -> Component.translatable("ontime.command.trigger.added",
                name, describe(trigger),
                Component.translatable("ontime.trigger.action." + trigger.action().lower())), true);

        // Stored either way — the pack may install FTB Quests later — but a
        // trigger that silently never fires is the kind of thing people spend
        // an evening on before checking their mod list.
        boolean ftb = trigger.kind() == com.mateof24.trigger.Trigger.Kind.FTB_QUEST
                || trigger.kind() == com.mateof24.trigger.Trigger.Kind.FTB_REWARD;
        if (ftb && !com.mateof24.platform.Services.PLATFORM.isModLoaded("ftbquests")) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.trigger.ftb_missing"), false);
        }
        return 1;
    }

    /** The index is the one {@code list} printed, so it is one-based here. */
    static int removeTrigger(CommandContext<CommandSourceStack> ctx, int index) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        if (!timerOpt.get().removeTrigger(index - 1)) {
            ctx.getSource().sendFailure(Component.translatable(
                    "ontime.command.trigger.invalid_index", index, name));
            return 0;
        }
        forget(name);
        TimerManager.getInstance().saveTimers();
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.trigger.removed", index, name), true);
        return 1;
    }

    static int clearTriggers(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        timerOpt.get().clearTriggers();
        forget(name);
        TimerManager.getInstance().saveTimers();
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.trigger.cleared", name), true);
        return 1;
    }

    /**
     * Drops what this timer has already fired.
     *
     * <p>Both memories, always. A quest already completed would otherwise keep
     * a brand new trigger from ever firing, and a fire raised for a trigger
     * that no longer exists would sit pending forever.</p>
     */
    private static void forget(String name) {
        com.mateof24.trigger.TriggerRegistry.resetFor(name);
        com.mateof24.trigger.FTBQuestsPoller.resetFor(name);
    }
}
