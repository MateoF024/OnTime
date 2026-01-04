package com.mateof24.command;

import com.mateof24.manager.TimerManager;
import com.mateof24.network.NetworkHandler;
import com.mateof24.timer.Timer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.Optional;

public class TimerCommands {

    // Register all timer commands
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("timer")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("hours", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 59))
                                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 59))
                                                        .executes(ctx -> createTimer(ctx, false))
                                                        .then(Commands.argument("countUp", BoolArgumentType.bool())
                                                                .executes(ctx -> createTimer(ctx, BoolArgumentType.getBool(ctx, "countUp")))
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("set")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("hours", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 59))
                                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 59))
                                                        .executes(TimerCommands::setTimer)
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("start")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(TimerCommands::startTimer)
                        )
                )
                .then(Commands.literal("pause")
                        .executes(TimerCommands::pauseTimer)
                )
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(TimerCommands::removeTimer)
                        )
                )
                .then(Commands.literal("add")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("hours", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 59))
                                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 59))
                                                        .executes(TimerCommands::addTime)
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("list")
                        .executes(TimerCommands::listTimers)
                )
        );
    }

    // Create timer command
    private static int createTimer(CommandContext<CommandSourceStack> ctx, boolean countUp) {
        String name = StringArgumentType.getString(ctx, "name");
        int hours = IntegerArgumentType.getInteger(ctx, "hours");
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");

        if (TimerManager.getInstance().createTimer(name, hours, minutes, seconds, countUp)) {
            String time = String.format("%02d:%02d:%02d", hours, minutes, seconds);
            String mode = countUp ? "count-up" : "countdown";
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§aTimer '%s' created as %s with time %s", name, mode, time)), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal(String.format("§cTimer '%s' already exists", name)));
            return 0;
        }
    }

    // Set timer time command
    private static int setTimer(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        int hours = IntegerArgumentType.getInteger(ctx, "hours");
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");

        if (TimerManager.getInstance().setTimerTime(name, hours, minutes, seconds)) {
            String time = String.format("%02d:%02d:%02d", hours, minutes, seconds);
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§aTimer '%s' set to %s", name, time)), true);

            syncIfActive(ctx, name);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal(String.format("§cTimer '%s' not found", name)));
            return 0;
        }
    }

    // Start timer command
    private static int startTimer(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");

        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(String.format("§cTimer '%s' not found", name)));
            return 0;
        }

        Timer timer = timerOpt.get();
        if (timer.isRunning()) {
            ctx.getSource().sendFailure(Component.literal(String.format("§cTimer '%s' is already running", name)));
            return 0;
        }

        Optional<Timer> activeTimer = TimerManager.getInstance().getActiveTimer();
        if (activeTimer.isPresent()) {
            ctx.getSource().sendFailure(Component.literal(String.format("§cTimer '%s' is currently active. Pause it first", activeTimer.get().getName())));
            return 0;
        }

        if (TimerManager.getInstance().startTimer(name)) {
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§aTimer '%s' started", name)), true);

            NetworkHandler.syncTimerToClients(
                    ctx.getSource().getServer(),
                    timer.getName(),
                    timer.getCurrentTicks(),
                    timer.getTargetTicks(),
                    timer.isCountUp(),
                    true
            );
            return 1;
        }

        return 0;
    }
    // Pause timer command
    private static int pauseTimer(CommandContext<CommandSourceStack> ctx) {
        Optional<Timer> activeTimer = TimerManager.getInstance().getActiveTimer();

        if (activeTimer.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("§cNo active timer to pause"));
            return 0;
        }

        Timer timer = activeTimer.get();

        if (timer.isRunning()) {
            timer.setRunning(false);
            TimerManager.getInstance().saveTimers();

            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§aTimer '%s' paused", timer.getName())), true);

            NetworkHandler.syncTimerToClients(
                    ctx.getSource().getServer(),
                    timer.getName(),
                    timer.getCurrentTicks(),
                    timer.getTargetTicks(),
                    timer.isCountUp(),
                    false
            );
            return 1;
        } else {
            timer.setRunning(true);
            TimerManager.getInstance().saveTimers();

            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§aTimer '%s' resumed", timer.getName())), true);

            NetworkHandler.syncTimerToClients(
                    ctx.getSource().getServer(),
                    timer.getName(),
                    timer.getCurrentTicks(),
                    timer.getTargetTicks(),
                    timer.isCountUp(),
                    true
            );
            return 1;
        }
    }

    // Remove timer command
    private static int removeTimer(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");

        if (TimerManager.getInstance().removeTimer(name)) {
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§aTimer '%s' removed", name)), true);

            NetworkHandler.syncTimerToClients(ctx.getSource().getServer(), "", 0, 0, false, false);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal(String.format("§cTimer '%s' not found", name)));
            return 0;
        }
    }

    // Add time to timer command
    private static int addTime(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        int hours = IntegerArgumentType.getInteger(ctx, "hours");
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");

        if (TimerManager.getInstance().addTimerTime(name, hours, minutes, seconds)) {
            String time = String.format("%02d:%02d:%02d", hours, minutes, seconds);
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§aAdded %s to timer '%s'", time, name)), true);

            syncIfActive(ctx, name);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal(String.format("§cTimer '%s' not found", name)));
            return 0;
        }
    }

    // Helper to sync timer if it's active
    private static void syncIfActive(CommandContext<CommandSourceStack> ctx, String name) {
        Optional<Timer> activeTimer = TimerManager.getInstance().getActiveTimer();
        if (activeTimer.isPresent() && activeTimer.get().getName().equals(name)) {
            Timer timer = activeTimer.get();
            NetworkHandler.syncTimerToClients(
                    ctx.getSource().getServer(),
                    timer.getName(),
                    timer.getCurrentTicks(),
                    timer.getTargetTicks(),
                    timer.isCountUp(),
                    timer.isRunning()
            );
        }
    }

    private static int listTimers(CommandContext<CommandSourceStack> ctx) {
        Map<String, Timer> timers = TimerManager.getInstance().getAllTimers();

        if (timers.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No timers found"), false);
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("=== Timers ==="), false);

        Optional<Timer> activeTimer = TimerManager.getInstance().getActiveTimer();

        for (Timer timer : timers.values()) {
            String status = timer.isRunning() ? "§a[RUNNING]" : "§7[STOPPED]";
            String active = activeTimer.isPresent() && activeTimer.get().getName().equals(timer.getName()) ? " §e*" : "";
            String type = timer.isCountUp() ? "↑" : "↓";

            String message = String.format("%s %s %s - %s%s",
                    status, timer.getName(), type, timer.getFormattedTime(), active);

            ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        }

        return 1;
    }
}