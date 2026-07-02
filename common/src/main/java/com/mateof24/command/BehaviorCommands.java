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

    static int viewTimerCommand(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        String command = timerOpt.get().getCommand();
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.command.current", name,
                        command != null && !command.isEmpty() ? command : "(none)"), false);
        return 1;
    }

    static int updateTimerCommand(CommandContext<CommandSourceStack> ctx, String command) {
        String name = StringArgumentType.getString(ctx, "name");

        if (!command.isEmpty()) {
            com.mateof24.validation.CommandValidator.ValidationResult validation =
                    com.mateof24.validation.CommandValidator.validate(command);
            if (!validation.isValid()) {
                ctx.getSource().sendFailure(validation.getErrorMessage());
                return 0;
            }
        }

        if (TimerManager.getInstance().setTimerCommand(name, command)) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.command.set", name, command), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
        return 0;
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

    static int viewCondition(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        Timer timer = timerOpt.get();
        if (!timer.hasCondition()) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.condition.none", name), false);
        } else {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.condition.current",
                            name, timer.getConditionObjective(),
                            timer.getConditionScore(), timer.getConditionTarget()), false);
        }
        return 1;
    }

    static int setCondition(CommandContext<CommandSourceStack> ctx,
                            String objective, int score, String target) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        timerOpt.get().setCondition(objective, score, target);
        TimerManager.getInstance().saveTimers();
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.condition.set",
                        name, objective, score, target), true);
        return 1;
    }

    static int setCondition(CommandContext<CommandSourceStack> ctx,
                            String objective, int score, String target, String action) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        timerOpt.get().setCondition(objective, score, target);
        timerOpt.get().setScoreConditionAction(action);
        TimerManager.getInstance().saveTimers();
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.condition.set", name, objective, score, target), true);
        return 1;
    }

    static int clearCondition(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        timerOpt.get().clearCondition();
        TimerManager.getInstance().saveTimers();
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.condition.cleared", name), true);
        return 1;
    }

    static int setConditionExpression(CommandContext<CommandSourceStack> ctx, String expression, String action) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        Optional<Boolean> test = com.mateof24.command.ConditionEvaluator
                .evaluate(expression, ctx.getSource().getServer(), timerOpt.get());
        if (test.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.expr.invalid", expression));
            return 0;
        }
        timerOpt.get().setConditionExpression(expression);
        timerOpt.get().setConditionExpressionAction(action);
        TimerManager.getInstance().saveTimers();
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.condition.expr.set", name, expression, action), true);
        return 1;
    }

    static int viewTrigger(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        String trigger = timerOpt.get().getTriggerType();
        String action  = timerOpt.get().getTriggerAction();
        ctx.getSource().sendSuccess(() ->
                trigger != null
                        ? Component.translatable("ontime.command.trigger.current", name, trigger, action)
                        : Component.translatable("ontime.command.trigger.none", name), false);
        return 1;
    }

    static int setTrigger(CommandContext<CommandSourceStack> ctx, String type, String action) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        timerOpt.get().setTriggerType(type);
        timerOpt.get().setTriggerAction(action);
        TimerManager.getInstance().saveTimers();
        com.mateof24.trigger.FTBQuestsPoller.resetFor(name);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.trigger.set", name, type, action), true);
        return 1;
    }

    static int clearTrigger(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        timerOpt.get().setTriggerType(null);
        TimerManager.getInstance().saveTimers();
        com.mateof24.trigger.FTBQuestsPoller.resetFor(name);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.trigger.cleared", name), true);
        return 1;
    }
}
