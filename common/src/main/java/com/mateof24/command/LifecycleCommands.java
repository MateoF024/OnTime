package com.mateof24.command;

import com.mateof24.config.ModConfig;
import com.mateof24.manager.TimerManager;
import com.mateof24.platform.Services;
import com.mateof24.tick.TimerTickHandler;
import com.mateof24.timer.Timer;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * Handlers for the timer lifecycle subcommands:
 * create / set / start / pause / remove / add / stop / reset and the
 * expression variants (expr create / expr set / expr add).
 * The command tree itself is registered by {@link TimerCommands}.
 */
final class LifecycleCommands {

    private LifecycleCommands() {}

    static int createTimer(CommandContext<CommandSourceStack> ctx, boolean countUp) {
        String name = StringArgumentType.getString(ctx, "name");
        int hours = IntegerArgumentType.getInteger(ctx, "hours");
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");

        long totalSeconds = hours * 3600L + minutes * 60L + seconds;
        long maxSeconds = ModConfig.getInstance().getMaxTimerSeconds();

        if (totalSeconds > maxSeconds) {
            ctx.getSource().sendFailure(
                    Component.translatable("ontime.command.error.maxtime", TimerCommands.formatTime(maxSeconds))
            );
            return 0;
        }

        if (TimerManager.getInstance().createTimer(name, hours, minutes, seconds, countUp)) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.create.success", name,
                            String.format("%02d:%02d:%02d", hours, minutes, seconds),
                            Component.translatable(countUp ? "ontime.mode.countup" : "ontime.mode.countdown")), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.create.exists", name));
            return 0;
        }
    }

    static int createTimerWithCommand(CommandContext<CommandSourceStack> ctx, boolean countUp, String command) {
        String name = StringArgumentType.getString(ctx, "name");
        int hours = IntegerArgumentType.getInteger(ctx, "hours");
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");

        long totalSeconds = hours * 3600L + minutes * 60L + seconds;
        long maxSeconds = ModConfig.getInstance().getMaxTimerSeconds();

        if (totalSeconds > maxSeconds) {
            ctx.getSource().sendFailure(
                    Component.translatable("ontime.command.error.maxtime", TimerCommands.formatTime(maxSeconds))
            );
            return 0;
        }

        // Validar comando si se proporcionó
        if (command != null && !command.isEmpty()) {
            com.mateof24.validation.CommandValidator.ValidationResult validation =
                    com.mateof24.validation.CommandValidator.validate(command);

            if (!validation.isValid()) {
                ctx.getSource().sendFailure(validation.getErrorMessage());
                return 0;
            }
        }

        if (TimerManager.getInstance().createTimer(name, hours, minutes, seconds, countUp)) {
            // Asignar comando personalizado si se proporcionó
            if (command != null && !command.isEmpty()) {
                TimerManager.getInstance().getTimer(name).ifPresent(timer -> {
                    timer.setCommand(command);
                    TimerManager.getInstance().saveTimers();
                });
            }

            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.create.success", name,
                            String.format("%02d:%02d:%02d", hours, minutes, seconds),
                            Component.translatable(countUp ? "ontime.mode.countup" : "ontime.mode.countdown")), true);

            // Mensaje adicional si se asignó comando
            if (command != null && !command.isEmpty()) {
                ctx.getSource().sendSuccess(() ->
                        Component.translatable("ontime.command.command_set", command), false);
            }

            return 1;
        } else {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.create.exists", name));
            return 0;
        }
    }

    static int setTimer(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        int hours = IntegerArgumentType.getInteger(ctx, "hours");
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");

        long totalSeconds = hours * 3600L + minutes * 60L + seconds;
        long maxSeconds = ModConfig.getInstance().getMaxTimerSeconds();

        if (totalSeconds > maxSeconds) {
            ctx.getSource().sendFailure(
                    Component.translatable("ontime.command.error.maxtime", TimerCommands.formatTime(maxSeconds))
            );
            return 0;
        }

        if (TimerManager.getInstance().setTimerTime(name, hours, minutes, seconds)) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.set.success", name,
                            String.format("%02d:%02d:%02d", hours, minutes, seconds)), true);

            syncIfActive(ctx, name);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
    }

    static int startTimer(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");

        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }

        Timer timer = timerOpt.get();
        if (timer.isRunning()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.start.running", name));
            return 0;
        }

        Optional<Timer> activeTimer = TimerManager.getInstance().getActiveTimer();
        if (activeTimer.isPresent() || TimerTickHandler.hasPendingCooldown()) {
            String activeName = activeTimer.map(Timer::getName).orElse("(cooldown)");
            ctx.getSource().sendFailure(Component.translatable("ontime.command.start.active", activeName));
            return 0;
        }

        if (TimerManager.getInstance().startTimer(name)) {
            timer = TimerManager.getInstance().getTimer(name).orElseThrow();

            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.start.success", name), true);

            Services.PLATFORM.sendTimerSyncPacket(
                    ctx.getSource().getServer(),
                    timer.getName(),
                    timer.getCurrentTicks(),
                    timer.getTargetTicks(),
                    timer.isCountUp(),
                    timer.isRunning(),
                    timer.isSilent()
            );
            return 1;
        }

        return 0;
    }

    static int pauseTimer(CommandContext<CommandSourceStack> ctx) {
        Optional<Timer> activeTimer = TimerManager.getInstance().getActiveTimer();

        if (activeTimer.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.pause.none"));
            return 0;
        }

        Timer timer = activeTimer.get();

        if (timer.isRunning()) {
            timer.setRunning(false);
            TimerManager.getInstance().saveTimers();

            com.mateof24.event.TimerEventBus.fireOnPause(
                    new com.mateof24.api.TimerInfo(timer.getName(), timer.getCurrentTicks(), timer.getTargetTicks(),
                            timer.isCountUp(), false, timer.isSilent(), timer.getCommand(),
                            timer.isRepeat(), timer.getRepeatCount(), timer.getRepeatsDone()));

            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.pause.success", timer.getName()), true);

            Services.PLATFORM.sendTimerSyncPacket(
                    ctx.getSource().getServer(),
                    timer.getName(),
                    timer.getCurrentTicks(),
                    timer.getTargetTicks(),
                    timer.isCountUp(),
                    false,
                    timer.isSilent()
            );
            return 1;
        } else {
            TimerManager.getInstance().reloadCommandsFromDisk();
            timer.setRunning(true);
            TimerManager.getInstance().saveTimers();

            com.mateof24.event.TimerEventBus.fireOnResume(
                    new com.mateof24.api.TimerInfo(timer.getName(), timer.getCurrentTicks(), timer.getTargetTicks(),
                            timer.isCountUp(), true, timer.isSilent(), timer.getCommand(),
                            timer.isRepeat(), timer.getRepeatCount(), timer.getRepeatsDone()));

            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.resume.success", timer.getName()), true);

            Services.PLATFORM.sendTimerSyncPacket(
                    ctx.getSource().getServer(),
                    timer.getName(),
                    timer.getCurrentTicks(),
                    timer.getTargetTicks(),
                    timer.isCountUp(),
                    true,
                    timer.isSilent()
            );
            return 1;
        }
    }

    static int removeTimer(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");

        if (TimerManager.getInstance().removeTimer(name)) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.remove.success", name), true);

            Services.PLATFORM.sendTimerSyncPacket(ctx.getSource().getServer(), "", 0, 0, false, false, false);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
    }

    static int addTime(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        int hours = IntegerArgumentType.getInteger(ctx, "hours");
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");

        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }

        Timer timer = timerOpt.get();
        long currentSeconds = timer.getCurrentTicks() / 20L;
        long additionalSeconds = hours * 3600L + minutes * 60L + seconds;
        long newTotalSeconds = currentSeconds + additionalSeconds;
        long maxSeconds = ModConfig.getInstance().getMaxTimerSeconds();

        if (newTotalSeconds > maxSeconds) {
            ctx.getSource().sendFailure(
                    Component.translatable("ontime.command.error.maxtime.add", TimerCommands.formatTime(maxSeconds))
            );
            return 0;
        }

        if (TimerManager.getInstance().addTimerTime(name, hours, minutes, seconds)) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.add.success",
                            String.format("%02d:%02d:%02d", hours, minutes, seconds), name), true);

            syncIfActive(ctx, name);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
    }

    static int stopTimer(CommandContext<CommandSourceStack> ctx) {
        Optional<Timer> activeTimer = TimerManager.getInstance().getActiveTimer();
        boolean hasCooldown = TimerTickHandler.hasPendingCooldown();

        if (activeTimer.isEmpty() && !hasCooldown) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.stop.none"));
            return 0;
        }

        TimerTickHandler.cancelCooldown();

        if (activeTimer.isPresent()) {
            Timer timer = activeTimer.get();
            timer.resetRepeatsDone();
            timer.reset();
            TimerManager.getInstance().clearActiveTimer();
            TimerManager.getInstance().saveTimers();
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.stop.success", timer.getName()), true);
        } else {
            TimerManager.getInstance().saveTimers();
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.stop.cooldown_cancelled"), true);
        }

        Services.PLATFORM.sendTimerSyncPacket(ctx.getSource().getServer(), "", 0, 0, false, false, false);
        return 1;
    }

    static int resetCurrentTimer(CommandContext<CommandSourceStack> ctx) {
        Optional<Timer> activeTimer = TimerManager.getInstance().getActiveTimer();

        if (activeTimer.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.reset.noactive"));
            return 0;
        }

        TimerTickHandler.cancelCooldown();

        Timer timer = activeTimer.get();
        boolean wasRunning = timer.isRunning();
        timer.reset();
        TimerManager.getInstance().saveTimers();

        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.reset.success", timer.getName()), true);

        if (wasRunning) {
            Services.PLATFORM.sendTimerSyncPacket(
                    ctx.getSource().getServer(),
                    timer.getName(), timer.getCurrentTicks(), timer.getTargetTicks(),
                    timer.isCountUp(), false, timer.isSilent());
        }
        return 1;
    }

    static int resetNamedTimer(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);

        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }

        Timer timer = timerOpt.get();
        boolean wasActive = TimerManager.getInstance().getActiveTimer()
                .map(t -> t.getName().equals(name))
                .orElse(false);
        boolean wasRunning = timer.isRunning();

        timer.reset();
        TimerManager.getInstance().saveTimers();

        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.reset.success", name), true);

        if (wasActive && wasRunning) {
            Services.PLATFORM.sendTimerSyncPacket(
                    ctx.getSource().getServer(),
                    timer.getName(),
                    timer.getCurrentTicks(),
                    timer.getTargetTicks(),
                    timer.isCountUp(),
                    false,
                    timer.isSilent()
            );
        }

        return 1;
    }

    static int createTimerWithExpr(CommandContext<CommandSourceStack> ctx, boolean countUp) {
        String name = StringArgumentType.getString(ctx, "name");
        String expression = StringArgumentType.getString(ctx, "expression");
        ExpressionEvaluator.Result resolved = ExpressionEvaluator.evaluateDetailed(expression, ctx.getSource().getServer());
        if (resolved.value.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.expr.invalid", expression)
                    .append(Component.literal(" (" + resolved.error + ")")));
            return 0;
        }
        long totalSeconds = resolved.value.getAsLong();
        long maxSeconds = ModConfig.getInstance().getMaxTimerSeconds();
        if (totalSeconds > maxSeconds) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.error.maxtime", TimerCommands.formatTime(maxSeconds)));
            return 0;
        }
        int h = (int)(totalSeconds / 3600), m = (int)((totalSeconds % 3600) / 60), s = (int)(totalSeconds % 60);
        if (TimerManager.getInstance().createTimer(name, h, m, s, countUp)) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.create.success", name,
                            String.format("%02d:%02d:%02d", h, m, s),
                            Component.translatable(countUp ? "ontime.mode.countup" : "ontime.mode.countdown")), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.translatable("ontime.command.create.exists", name));
        return 0;
    }

    static int setTimerExpr(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        String expression = StringArgumentType.getString(ctx, "expression");
        ExpressionEvaluator.Result resolved = ExpressionEvaluator.evaluateDetailed(expression, ctx.getSource().getServer());
        if (resolved.value.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.expr.invalid", expression)
                    .append(Component.literal(" (" + resolved.error + ")")));
            return 0;
        }
        long totalSeconds = resolved.value.getAsLong();
        long maxSeconds = ModConfig.getInstance().getMaxTimerSeconds();
        if (totalSeconds > maxSeconds) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.error.maxtime", TimerCommands.formatTime(maxSeconds)));
            return 0;
        }
        int h = (int)(totalSeconds / 3600), m = (int)((totalSeconds % 3600) / 60), s = (int)(totalSeconds % 60);
        if (TimerManager.getInstance().setTimerTime(name, h, m, s)) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.set.success", name,
                            String.format("%02d:%02d:%02d", h, m, s)), true);
            syncIfActive(ctx, name);
            return 1;
        }
        ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
        return 0;
    }

    static int addTimerExpr(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        String expression = StringArgumentType.getString(ctx, "expression");
        ExpressionEvaluator.Result resolved = ExpressionEvaluator.evaluateDetailed(expression, ctx.getSource().getServer());
        if (resolved.value.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.expr.invalid", expression)
                    .append(Component.literal(" (" + resolved.error + ")")));
            return 0;
        }
        long addSeconds = resolved.value.getAsLong();
        long maxSeconds = ModConfig.getInstance().getMaxTimerSeconds();

        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }

        long currentSeconds = timerOpt.get().getCurrentTicks() / 20L;
        long newTotalSeconds = currentSeconds + addSeconds;
        if (newTotalSeconds > maxSeconds) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.error.maxtime.add", TimerCommands.formatTime(maxSeconds)));
            return 0;
        }

        int h = (int)(addSeconds / 3600), m = (int)((addSeconds % 3600) / 60), s = (int)(addSeconds % 60);
        if (TimerManager.getInstance().addTimerTime(name, h, m, s)) {
            final String fmt = String.format("%02d:%02d:%02d", h, m, s);
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.add.success", fmt, name), true);
            syncIfActive(ctx, name);
            return 1;
        }
        ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
        return 0;
    }

    private static void syncIfActive(CommandContext<CommandSourceStack> ctx, String name) {
        Optional<Timer> activeTimer = TimerManager.getInstance().getActiveTimer();
        if (activeTimer.isPresent() && activeTimer.get().getName().equals(name)) {
            Timer timer = activeTimer.get();
            Services.PLATFORM.sendTimerSyncPacket(
                    ctx.getSource().getServer(),
                    timer.getName(),
                    timer.getCurrentTicks(),
                    timer.getTargetTicks(),
                    timer.isCountUp(),
                    timer.isRunning(),
                    timer.isSilent()
            );
        }
    }
}
