package com.mateof24.command;

import com.mateof24.config.ModConfig;
import com.mateof24.config.TimerPositionPreset;
import com.mateof24.manager.DisplaySlots;
import com.mateof24.manager.TimerManager;
import com.mateof24.timer.Timer;
import com.mateof24.timer.TimerRun;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Handlers for the query subcommands: status and list.
 * (help is wired directly to {@link HelpSystem} from the tree.)
 * The command tree itself is registered by {@link TimerCommands}.
 */
final class InfoCommands {

    private InfoCommands() {}

    /** How many finish commands are spelled out before the rest are counted. */
    private static final int MAX_LISTED_COMMANDS = 3;

    private static final String[] TITLE_SLOTS = {"above", "below", "left", "right"};

    /**
     * {@code /timer gui} — opens the administration panel.
     *
     * <p>The server checks the permission, registers the player as a
     * subscriber and pushes the first snapshot; the client opens the screen
     * when it arrives. There is deliberately no way in from the main menu:
     * the panel has no state without a server, so it cannot exist without
     * one.</p>
     */
    static int openPanel(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.players_only"));
            return 0;
        }
        com.mateof24.admin.AdminHandler.open(ctx.getSource().getServer(), player);
        return 1;
    }

    /**
     * {@code /timer status <name>} — everything about one timer in one place.
     *
     * <p>This information was spread across eight query subcommands, each
     * printing one facet. That was a convenience while a timer was a single
     * thing with a single clock; with several executions of it running for
     * different audiences, no one of those subcommands can answer "what is this
     * timer doing right now" any more.</p>
     *
     * <p>Rows that hold nothing are left out. A timer with no trigger says so
     * by not having a trigger line, which keeps a plain countdown to four lines
     * instead of fourteen mostly saying "none".</p>
     */
    static int status(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Timer timer = TimerManager.getInstance().getTimer(name).orElse(null);
        if (timer == null) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }

        CommandSourceStack source = ctx.getSource();
        List<Component> lines = new ArrayList<>();

        lines.add(Component.translatable("ontime.command.status.header", name));
        lines.add(CommandFormat.row("ontime.status.label.duration",
                Component.translatable("ontime.command.status.duration",
                        CommandFormat.duration(timer.getTargetTicks()),
                        Component.translatable(timer.isCountUp()
                                ? "ontime.mode.countup" : "ontime.mode.countdown"))));

        lines.add(CommandFormat.row("ontime.status.label.display", display(timer)));

        if (timer.isSilent()) {
            lines.add(CommandFormat.row("ontime.status.label.sound",
                    Component.translatable("ontime.status.value.silent")));
        }

        if (timer.hasTitles()) {
            lines.add(CommandFormat.row("ontime.status.label.titles", titles(timer)));
        }

        Component finish = finishCommands(timer);
        if (finish != null) lines.add(CommandFormat.row("ontime.status.label.finish", finish));

        if (timer.hasScheduledCommands()) {
            lines.add(CommandFormat.row("ontime.status.label.scheduled",
                    Component.translatable("ontime.command.status.scheduled",
                            timer.getCommandEvents().size())));
        }

        if (timer.isRepeat()) {
            long cd = timer.getRepeatCooldownTicks() / 20L;
            lines.add(CommandFormat.row("ontime.status.label.repeat",
                    Component.translatable("ontime.command.status.repeat",
                            timer.getRepeatCount() == -1
                                    ? Component.translatable("ontime.command.status.repeat.infinite")
                                    : Component.literal(String.valueOf(timer.getRepeatCount())),
                            cd)));
        }

        if (timer.getNextTimer() != null) {
            lines.add(CommandFormat.row("ontime.status.label.sequence",
                    Component.translatable("ontime.command.status.sequence",
                            timer.getNextTimer(), timer.getSequenceCooldownTicks() / 20L)));
        }

        if (timer.hasCondition()) {
            lines.add(CommandFormat.row("ontime.status.label.condition",
                    Component.translatable("ontime.command.status.condition",
                            timer.getConditionObjective(), timer.getConditionScore(),
                            timer.getConditionTarget(), timer.getScoreConditionAction())));
        }

        if (timer.getConditionExpression() != null) {
            lines.add(CommandFormat.row("ontime.status.label.expression",
                    Component.translatable("ontime.command.status.expression",
                            timer.getConditionExpression(), timer.getConditionExpressionAction())));
        }

        if (timer.getTriggerType() != null) {
            lines.add(CommandFormat.row("ontime.status.label.trigger",
                    Component.translatable("ontime.command.status.trigger",
                            timer.getTriggerType(), timer.getTriggerAction())));
        }

        List<TimerRun> runs = TimerManager.getInstance().findRuns(name, null);
        if (runs.isEmpty()) {
            lines.add(CommandFormat.row("ontime.status.label.runs",
                    Component.translatable("ontime.command.status.runs.none")));
        } else {
            lines.add(CommandFormat.row("ontime.status.label.runs",
                    Component.translatable("ontime.command.status.runs", runs.size())));
            for (TimerRun run : runs) {
                lines.add(Component.translatable("ontime.command.status.run",
                        run.shortId(),
                        CommandFormat.mode(run),
                        CommandFormat.audience(source.getServer(), run.audience()),
                        CommandFormat.duration(run.getCurrentTicks()),
                        CommandFormat.runState(run)));
            }
        }

        for (Component line : lines) source.sendSuccess(() -> line, false);
        return 1;
    }

    /** Preset and scale, each marked when it comes from the global default. */
    private static Component display(Timer timer) {
        String preset = DisplaySlots.presetOf(timer);
        String presetName = TimerPositionPreset.valueOf(preset).getDisplayName();
        if (timer.getPosition() == null) {
            presetName = presetName + " " + inheritedMark();
        } else if (TimerPositionPreset.CUSTOM.name().equals(preset)) {
            ModConfig config = ModConfig.getInstance();
            int x = timer.getTimerX() != null ? timer.getTimerX() : config.getTimerX();
            int y = timer.getTimerY() != null ? timer.getTimerY() : config.getTimerY();
            presetName = presetName + " (" + x + ", " + y + ")";
        }

        float scale = timer.getScale() != null ? timer.getScale() : ModConfig.getInstance().getTimerScale();
        String scaleText = String.valueOf(scale) + (timer.getScale() == null ? " " + inheritedMark() : "");
        return Component.translatable("ontime.command.status.display", presetName, scaleText);
    }

    private static String inheritedMark() {
        return "§7(*)§f";
    }

    private static Component titles(Timer timer) {
        List<String> parts = new ArrayList<>();
        for (String slot : TITLE_SLOTS) {
            String raw = timer.getTitle(slot);
            if (raw != null) parts.add(slot + ": " + raw);
        }
        return Component.literal(String.join("  ", parts));
    }

    /**
     * The finish commands, legacy field first. Long lists are cut off — this is
     * an overview, and {@code /timer commands <name> list} is the full one.
     */
    private static Component finishCommands(Timer timer) {
        List<String> all = new ArrayList<>();
        String legacy = timer.getCommand();
        if (legacy != null && !legacy.trim().isEmpty()) all.add(legacy);
        all.addAll(timer.getFinishCommands());
        if (all.isEmpty()) return null;

        if (all.size() <= MAX_LISTED_COMMANDS) return Component.literal(String.join("  |  ", all));
        String shown = String.join("  |  ", all.subList(0, MAX_LISTED_COMMANDS));
        return Component.translatable("ontime.command.status.commands_more",
                shown, all.size() - MAX_LISTED_COMMANDS);
    }

    /**
     * {@code /timer list} — grouped by whether anything is running, with the
     * audience of each execution.
     *
     * <p>The 4.0.0 list marked one timer with a star, because exactly one could
     * be active. A star cannot express three executions of one timer for three
     * different audiences, so the running ones are listed first with their own
     * line each and the rest follow as definitions.</p>
     */
    static int listTimers(CommandContext<CommandSourceStack> ctx) {
        java.util.Collection<Timer> timers = TimerManager.getInstance().timersView();

        if (timers.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("ontime.command.list.empty"), false);
            return 0;
        }

        CommandSourceStack source = ctx.getSource();
        List<Timer> running = new ArrayList<>();
        List<Timer> idle = new ArrayList<>();
        for (Timer timer : timers) {
            (TimerManager.getInstance().hasRunOf(timer.getName()) ? running : idle).add(timer);
        }

        source.sendSuccess(() -> Component.translatable("ontime.command.list.header"), false);

        if (!running.isEmpty()) {
            source.sendSuccess(() ->
                    Component.translatable("ontime.command.list.group.running", running.size()), false);
            for (Timer timer : running) {
                for (TimerRun run : TimerManager.getInstance().findRuns(timer.getName(), null)) {
                    Component line = Component.translatable("ontime.command.list.entry.running",
                            timer.getName(),
                            timer.isCountUp() ? "↑" : "↓",
                            CommandFormat.duration(run.getCurrentTicks()),
                            CommandFormat.audience(source.getServer(), run.audience()),
                            CommandFormat.runState(run));
                    source.sendSuccess(() -> line, false);
                }
            }
        }

        if (!idle.isEmpty()) {
            source.sendSuccess(() ->
                    Component.translatable("ontime.command.list.group.stopped", idle.size()), false);
            for (Timer timer : idle) {
                Component line = Component.translatable("ontime.command.list.entry.stopped",
                        timer.getName(),
                        timer.isCountUp() ? "↑" : "↓",
                        CommandFormat.duration(timer.getTargetTicks()),
                        timer.isSilent() ? " §7[S]" : "");
                source.sendSuccess(() -> line, false);
            }
        }

        return timers.size();
    }
}
