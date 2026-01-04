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
        );
    }

    // Create timer command
    private static int createTimer(CommandContext<CommandSourceStack> ctx, boolean countUp) {
        String name = StringArgumentType.getString(ctx, "name");
        int hours = IntegerArgumentType.getInteger(ctx, "hours");
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");

        if (TimerManager.getInstance().createTimer(name, hours, minutes, seconds, countUp)) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("commands.ontime.create.success", name), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(
                    Component.translatable("commands.ontime.create.exists", name));
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
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("commands.ontime.set.success", name), true);

            // Sync if active
            syncIfActive(ctx, name);
            return 1;
        } else {
            ctx.getSource().sendFailure(
                    Component.translatable("commands.ontime.timer.notfound", name));
            return 0;
        }
    }

    // Start timer command
    private static int startTimer(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");

        if (TimerManager.getInstance().startTimer(name)) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("commands.ontime.start.success", name), true);

            // Sync to all clients
            Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
            if (timerOpt.isPresent()) {
                Timer timer = timerOpt.get();
                NetworkHandler.syncTimerToClients(
                        ctx.getSource().getServer(),
                        timer.getName(),
                        timer.getCurrentTicks(),
                        timer.getTargetTicks(),
                        timer.isCountUp(),
                        true
                );
            }
            return 1;
        } else {
            ctx.getSource().sendFailure(
                    Component.translatable("commands.ontime.timer.notfound", name));
            return 0;
        }
    }

    // Pause timer command
    private static int pauseTimer(CommandContext<CommandSourceStack> ctx) {
        if (TimerManager.getInstance().pauseTimer()) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("commands.ontime.pause.success"), true);

            // Clear on clients
            NetworkHandler.syncTimerToClients(ctx.getSource().getServer(), "", 0, 0, false, false);
            return 1;
        } else {
            ctx.getSource().sendFailure(
                    Component.translatable("commands.ontime.pause.noactive"));
            return 0;
        }
    }

    // Remove timer command
    private static int removeTimer(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");

        if (TimerManager.getInstance().removeTimer(name)) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("commands.ontime.remove.success", name), true);

            // Clear on clients if it was active
            NetworkHandler.syncTimerToClients(ctx.getSource().getServer(), "", 0, 0, false, false);
            return 1;
        } else {
            ctx.getSource().sendFailure(
                    Component.translatable("commands.ontime.timer.notfound", name));
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
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("commands.ontime.add.success", name), true);

            // Sync if active
            syncIfActive(ctx, name);
            return 1;
        } else {
            ctx.getSource().sendFailure(
                    Component.translatable("commands.ontime.timer.notfound", name));
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
}