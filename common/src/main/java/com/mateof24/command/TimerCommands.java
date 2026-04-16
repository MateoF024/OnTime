package com.mateof24.command;

import com.mateof24.config.TimerPositionPreset;
import com.mateof24.platform.Services;
import com.mateof24.config.ModConfig;
import com.mateof24.manager.TimerManager;
import com.mateof24.storage.PlayerPreferences;
import com.mateof24.storage.TimerStorage;
import com.mateof24.timer.Timer;
import com.mateof24.tick.TimerTickHandler;
import com.mateof24.permission.PermissionHelper;
import com.mateof24.permission.PermissionNodes;
import com.mateof24.webpanel.TimerWebPanel;
import com.mateof24.command.ExpressionEvaluator;
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
import java.util.UUID;
import java.util.OptionalLong;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;

public class TimerCommands {

    private static class TimerNameSuggestionProvider implements com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> {
        @Override
        public java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> getSuggestions(
                CommandContext<CommandSourceStack> context,
                com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {

            Map<String, Timer> timers = TimerManager.getInstance().getAllTimers();

            for (String timerName : timers.keySet()) {
                if (timerName.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                    builder.suggest(timerName);
                }
            }

            return builder.buildFuture();
        }
    }

    private static String formatTime(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static final TimerNameSuggestionProvider TIMER_SUGGESTIONS = new TimerNameSuggestionProvider();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("timer").requires(source ->
                        PermissionHelper.hasPermission(source, "ontime.command", 4))
                .then(Commands.literal("create")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_CREATE, 4))
                        .then(Commands.argument("name", StringArgumentType.word()).then(Commands.literal("expr")
                                                .then(Commands.argument("expression", StringArgumentType.greedyString())
                                                        .executes(ctx -> createTimerWithExpr(ctx, false))
                                                )
                                        )
                                .then(Commands.argument("hours", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 59))
                                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 59))
                                                        .executes(ctx -> createTimer(ctx, false))
                                                        .then(Commands.argument("countUp", BoolArgumentType.bool())
                                                                .executes(ctx -> createTimer(ctx, BoolArgumentType.getBool(ctx, "countUp")))
                                                                .then(Commands.argument("command", StringArgumentType.greedyString())
                                                                        .executes(ctx -> createTimerWithCommand(ctx,
                                                                                BoolArgumentType.getBool(ctx, "countUp"),
                                                                                StringArgumentType.getString(ctx, "command")))
                                                                )
                                                        )
                                                        .then(Commands.argument("command", StringArgumentType.greedyString())
                                                                .executes(ctx -> createTimerWithCommand(ctx, false,
                                                                        StringArgumentType.getString(ctx, "command")))
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("set")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_SET, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS).then(Commands.literal("expr")
                                        .then(Commands.argument("expression", StringArgumentType.greedyString())
                                                .executes(TimerCommands::setTimerExpr)
                                        )
                                )
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
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_START, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(TimerCommands::startTimer)
                        )
                )
                .then(Commands.literal("pause")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_PAUSE, 4))
                        .executes(TimerCommands::pauseTimer)
                )
                .then(Commands.literal("remove")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_REMOVE, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(TimerCommands::removeTimer)
                        )
                )
                .then(Commands.literal("add")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_ADD, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS).then(Commands.literal("expr")
                                        .then(Commands.argument("expression", StringArgumentType.greedyString())
                                                .executes(TimerCommands::addTimerExpr)
                                        )
                                )
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
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_LIST, 4))
                        .executes(TimerCommands::listTimers)
                )
                .then(Commands.literal("silent")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_SILENT, 4))
                        .executes(TimerCommands::toggleSilentSelf)
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(TimerCommands::toggleSilentTargets)
                        )
                )
                .then(Commands.literal("hide")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_HIDE, 4))
                        .executes(TimerCommands::toggleHideSelf)
                        .then(Commands.argument("targets", net.minecraft.commands.arguments.EntityArgument.players())
                                .executes(TimerCommands::toggleHideTargets)
                        )
                )
                .then(Commands.literal("stop")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_STOP, 4))
                        .executes(TimerCommands::stopTimer)
                )
                .then(Commands.literal("reset")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_RESET, 4))
                        .executes(TimerCommands::resetCurrentTimer)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(TimerCommands::resetNamedTimer)
                        )
                )
                .then(Commands.literal("help")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_HELP, 4))
                        .executes(ctx -> HelpSystem.showHelpPage(ctx.getSource(), 1))
                        .then(Commands.argument("pageOrCommand", StringArgumentType.word())
                                .executes(ctx -> {
                                    String arg = StringArgumentType.getString(ctx, "pageOrCommand");
                                    try {
                                        int page = Integer.parseInt(arg);
                                        return HelpSystem.showHelpPage(ctx.getSource(), page);
                                    } catch (NumberFormatException e) {
                                        return HelpSystem.showCommandHelp(ctx.getSource(), arg);
                                    }
                                })
                        )
                )
                .then(Commands.literal("position")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_POSITION, 4))
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (TimerPositionPreset preset : TimerPositionPreset.values()) {
                                        String presetName = preset.name().toLowerCase();
                                        if (presetName.startsWith(builder.getRemaining().toLowerCase()))
                                            builder.suggest(presetName);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(TimerCommands::setPosition)
                        )
                )
                .then(Commands.literal("sound")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_SOUND, 4))
                        .then(Commands.argument("soundId", ResourceLocationArgument.id())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggestResource(
                                                BuiltInRegistries.SOUND_EVENT.keySet(), builder))
                                .executes(ctx -> setSoundDefault(ctx,
                                        ResourceLocationArgument.getId(ctx, "soundId").toString()))
                                .then(Commands.argument("volume", FloatArgumentType.floatArg(0.0f, 1.0f))
                                        .executes(ctx -> setSoundWithVolume(ctx,
                                                ResourceLocationArgument.getId(ctx, "soundId").toString(),
                                                FloatArgumentType.getFloat(ctx, "volume")))
                                        .then(Commands.argument("pitch", FloatArgumentType.floatArg(0.5f, 2.0f))
                                                .executes(ctx -> setSoundFull(ctx,
                                                        ResourceLocationArgument.getId(ctx, "soundId").toString(),
                                                        FloatArgumentType.getFloat(ctx, "volume"),
                                                        FloatArgumentType.getFloat(ctx, "pitch")))
                                        )
                                )
                        )
                )
                .then(Commands.literal("scale")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_SCALE, 4))
                        .then(Commands.argument("scale", FloatArgumentType.floatArg(0.1f, 5.0f))
                                .executes(ctx -> setScale(ctx, FloatArgumentType.getFloat(ctx, "scale")))
                        )
                )
                .then(Commands.literal("command")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_COMMAND, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(TimerCommands::viewTimerCommand)
                                .then(Commands.argument("command", StringArgumentType.greedyString())
                                        .executes(ctx -> updateTimerCommand(ctx,
                                                StringArgumentType.getString(ctx, "command")))
                                )
                        )
                )
                .then(Commands.literal("repeat")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_REPEAT, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(TimerCommands::toggleRepeatInfinite)
                                .then(Commands.argument("count", IntegerArgumentType.integer(-1))
                                        .executes(ctx -> setRepeatCount(ctx,
                                                IntegerArgumentType.getInteger(ctx, "count"), 0))
                                        .then(Commands.argument("cooldownSeconds", IntegerArgumentType.integer(0))
                                                .executes(ctx -> setRepeatCount(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "count"),
                                                        IntegerArgumentType.getInteger(ctx, "cooldownSeconds")))
                                        )
                                )
                        )
                )
                .then(Commands.literal("sequence")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_SEQUENCE, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(TimerCommands::viewSequence)
                                .then(Commands.literal("clear")
                                        .executes(TimerCommands::clearSequence)
                                )
                                .then(Commands.argument("nextName", StringArgumentType.word())
                                        .suggests(TIMER_SUGGESTIONS)
                                        .executes(ctx -> setSequence(ctx,
                                                StringArgumentType.getString(ctx, "nextName"), 0))
                                        .then(Commands.argument("cooldownSeconds", IntegerArgumentType.integer(0))
                                                .executes(ctx -> setSequence(ctx,
                                                        StringArgumentType.getString(ctx, "nextName"),
                                                        IntegerArgumentType.getInteger(ctx, "cooldownSeconds")))
                                        )
                                )
                        )
                )
                .then(Commands.literal("condition")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_CONDITION, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(TimerCommands::viewCondition)
                                .then(Commands.literal("clear")
                                        .executes(TimerCommands::clearCondition)
                                )
                                .then(Commands.argument("objective", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            ctx.getSource().getServer().getScoreboard().getObjectives()
                                                    .forEach(obj -> {
                                                        if (obj.getName().toLowerCase().startsWith(builder.getRemaining().toLowerCase()))
                                                            builder.suggest(obj.getName());
                                                    });
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("score", IntegerArgumentType.integer(0))
                                                .executes(ctx -> setCondition(ctx,
                                                        StringArgumentType.getString(ctx, "objective"),
                                                        IntegerArgumentType.getInteger(ctx, "score"),
                                                        "*"))
                                                .then(Commands.argument("target", StringArgumentType.word())
                                                        .suggests((ctx, builder) -> {
                                                            String remaining = builder.getRemaining().toLowerCase();
                                                            if ("*".startsWith(remaining)) builder.suggest("*");
                                                            ctx.getSource().getServer().getPlayerList().getPlayers()
                                                                    .forEach(p -> {
                                                                        if (p.getScoreboardName().toLowerCase().startsWith(remaining))
                                                                            builder.suggest(p.getScoreboardName());
                                                                    });
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(ctx -> setCondition(ctx,
                                                                StringArgumentType.getString(ctx, "objective"),
                                                                IntegerArgumentType.getInteger(ctx, "score"),
                                                                StringArgumentType.getString(ctx, "target")))
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("export")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_EXPORT, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(ctx -> exportTimer(ctx, StringArgumentType.getString(ctx, "name")))
                        )
                )
                .then(Commands.literal("import")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_IMPORT, 4))
                        .then(Commands.argument("filename", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    TimerStorage.getExportNames().stream()
                                            .filter(n -> n.toLowerCase().startsWith(builder.getRemaining().toLowerCase()))
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> importTimer(ctx, StringArgumentType.getString(ctx, "filename"), null))
                                .then(Commands.argument("newname", StringArgumentType.word())
                                        .executes(ctx -> importTimer(ctx,
                                                StringArgumentType.getString(ctx, "filename"),
                                                StringArgumentType.getString(ctx, "newname")))
                                )
                        )
                )
                .then(Commands.literal("clone")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_CLONE, 4))
                        .then(Commands.argument("source", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .then(Commands.argument("dest", StringArgumentType.word())
                                        .executes(ctx -> cloneTimer(ctx,
                                                StringArgumentType.getString(ctx, "source"),
                                                StringArgumentType.getString(ctx, "dest")))
                                )
                        )
                )
                .then(Commands.literal("webpanel")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_WEBPANEL, 4))
                        .then(Commands.literal("start")
                                .executes(ctx -> webPanelStart(ctx, ModConfig.getInstance().getWebPanelPort()))
                                .then(Commands.argument("port", IntegerArgumentType.integer(1024, 65535))
                                        .executes(ctx -> webPanelStart(ctx, IntegerArgumentType.getInteger(ctx, "port")))
                                )
                        )
                        .then(Commands.literal("stop")
                                .executes(TimerCommands::webPanelStop)
                        )
                        .then(Commands.literal("info")
                                .executes(TimerCommands::webPanelInfo)
                        )
                )
        );
    }

    private static int createTimer(CommandContext<CommandSourceStack> ctx, boolean countUp) {
        String name = StringArgumentType.getString(ctx, "name");
        int hours = IntegerArgumentType.getInteger(ctx, "hours");
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");

        long totalSeconds = hours * 3600L + minutes * 60L + seconds;
        long maxSeconds = ModConfig.getInstance().getMaxTimerSeconds();

        if (totalSeconds > maxSeconds) {
            ctx.getSource().sendFailure(
                    Component.translatable("ontime.command.error.maxtime", formatTime(maxSeconds))
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

    private static int createTimerWithCommand(CommandContext<CommandSourceStack> ctx, boolean countUp, String command) {
        String name = StringArgumentType.getString(ctx, "name");
        int hours = IntegerArgumentType.getInteger(ctx, "hours");
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");

        long totalSeconds = hours * 3600L + minutes * 60L + seconds;
        long maxSeconds = ModConfig.getInstance().getMaxTimerSeconds();

        if (totalSeconds > maxSeconds) {
            ctx.getSource().sendFailure(
                    Component.translatable("ontime.command.error.maxtime", formatTime(maxSeconds))
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
                        Component.literal("§7Command set: §f" + command), false);
            }

            return 1;
        } else {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.create.exists", name));
            return 0;
        }
    }

    private static int setTimer(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        int hours = IntegerArgumentType.getInteger(ctx, "hours");
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");

        long totalSeconds = hours * 3600L + minutes * 60L + seconds;
        long maxSeconds = ModConfig.getInstance().getMaxTimerSeconds();

        if (totalSeconds > maxSeconds) {
            ctx.getSource().sendFailure(
                    Component.translatable("ontime.command.error.maxtime", formatTime(maxSeconds))
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

    private static int startTimer(CommandContext<CommandSourceStack> ctx) {
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

    private static int pauseTimer(CommandContext<CommandSourceStack> ctx) {
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

    private static int removeTimer(CommandContext<CommandSourceStack> ctx) {
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

    private static int addTime(CommandContext<CommandSourceStack> ctx) {
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
                    Component.translatable("ontime.command.error.maxtime.add", formatTime(maxSeconds))
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

    private static int toggleSilentSelf(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("§cThis command can only be used by players"));
            return 0;
        }

        UUID playerUUID = player.getUUID();
        boolean currentSilent = PlayerPreferences.getTimerSilent(playerUUID);
        boolean newSilent = !currentSilent;

        PlayerPreferences.setTimerSilent(playerUUID, newSilent);
        Services.PLATFORM.sendSilentPacket(player, newSilent);

        String targetKey = "ontime.command.silent.self";

        if (newSilent) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.silent.disabled_for",
                            Component.translatable(targetKey)), false);
        } else {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.silent.enabled_for",
                            Component.translatable(targetKey)), false);
        }

        return 1;
    }

    private static int toggleSilentTargets(CommandContext<CommandSourceStack> ctx) {
        try {
            var targets = EntityArgument.getPlayers(ctx, "targets");
            int count = 0;
            boolean newSilent = true;

            for (net.minecraft.server.level.ServerPlayer target : targets) {
                UUID playerUUID = target.getUUID();
                boolean currentSilent = PlayerPreferences.getTimerSilent(playerUUID);
                newSilent = !currentSilent;

                PlayerPreferences.setTimerSilent(playerUUID, newSilent);
                Services.PLATFORM.sendSilentPacket(target, newSilent);
                count++;
            }

            int finalCount = count;
            boolean finalSilent = newSilent;

            if (finalSilent) {
                ctx.getSource().sendSuccess(() ->
                        Component.translatable("ontime.command.silent.disabled_for",
                                Component.translatable("ontime.command.silent.players", finalCount)), true);
            } else {
                ctx.getSource().sendSuccess(() ->
                        Component.translatable("ontime.command.silent.enabled_for",
                                Component.translatable("ontime.command.silent.players", finalCount)), true);
            }

            return count;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("§cInvalid target selector"));
            return 0;
        }
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

    private static int listTimers(CommandContext<CommandSourceStack> ctx) {
        Map<String, Timer> timers = TimerManager.getInstance().getAllTimers();

        if (timers.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("ontime.command.list.empty"), false);
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.translatable("ontime.command.list.header"), false);

        Optional<Timer> activeTimer = TimerManager.getInstance().getActiveTimer();

        for (Timer timer : timers.values()) {
            Component statusComponent = Component.translatable(
                    timer.isRunning() ? "ontime.list.status.running" : "ontime.list.status.stopped"
            );
            String active = activeTimer.isPresent() && activeTimer.get().getName().equals(timer.getName()) ? " §e*" : "";
            String type = timer.isCountUp() ? "↑" : "↓";
            String silent = timer.isSilent() ? " §7[S]" : "";

            String message = String.format("%s §f%s §7%s - §f%s%s%s",
                    statusComponent.getString(), timer.getName(), type, timer.getFormattedTime(), active, silent);

            ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        }

        return 1;
    }

    private static int toggleHideSelf(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("§cThis command can only be used by players"));
            return 0;
        }

        UUID playerUUID = player.getUUID();
        boolean currentVisibility = PlayerPreferences.getTimerVisibility(playerUUID);
        boolean newVisibility = !currentVisibility;

        PlayerPreferences.setTimerVisibility(playerUUID, newVisibility);
        Services.PLATFORM.sendVisibilityPacket(player, newVisibility);

        String targetKey = "ontime.command.hide.self";

        if (newVisibility) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.hide.enabled",
                            Component.translatable(targetKey)), false);
        } else {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.hide.disabled",
                            Component.translatable(targetKey)), false);
        }

        return 1;
    }

    private static int toggleHideTargets(CommandContext<CommandSourceStack> ctx) {
        try {
            var targets = EntityArgument.getPlayers(ctx, "targets");
            int count = 0;
            boolean newVisibility = true;

            for (net.minecraft.server.level.ServerPlayer target : targets) {
                UUID playerUUID = target.getUUID();
                boolean currentVisibility = PlayerPreferences.getTimerVisibility(playerUUID);
                newVisibility = !currentVisibility;

                PlayerPreferences.setTimerVisibility(playerUUID, newVisibility);
                Services.PLATFORM.sendVisibilityPacket(target, newVisibility);
                count++;
            }

            int finalCount = count;
            boolean finalVisibility = newVisibility;

            if (newVisibility) {
                ctx.getSource().sendSuccess(() ->
                        Component.translatable("ontime.command.hide.enabled",
                                Component.translatable("ontime.command.hide.players", finalCount)), true);
            } else {
                ctx.getSource().sendSuccess(() ->
                        Component.translatable("ontime.command.hide.disabled",
                                Component.translatable("ontime.command.hide.players", finalCount)), true);
            }

            return count;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("§cInvalid target selector"));
            return 0;
        }
    }

    private static int stopTimer(CommandContext<CommandSourceStack> ctx) {
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

    private static int resetCurrentTimer(CommandContext<CommandSourceStack> ctx) {
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

    private static int resetNamedTimer(CommandContext<CommandSourceStack> ctx) {
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

    private static int setPosition(CommandContext<CommandSourceStack> ctx) {
        String presetName = StringArgumentType.getString(ctx, "preset");
        TimerPositionPreset preset = TimerPositionPreset.fromString(presetName);

        ModConfig.getInstance().setPositionPreset(preset);

        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.position.success", preset.getDisplayName()), true);
        return 1;
    }

    private static int setSoundDefault(CommandContext<CommandSourceStack> ctx, String soundId) {
        return setSound(ctx, soundId, 0.75f, 2.0f);
    }

    private static int setSoundWithVolume(CommandContext<CommandSourceStack> ctx, String soundId, float volume) {
        return setSound(ctx, soundId, volume, 2.0f);
    }

    private static int setSoundFull(CommandContext<CommandSourceStack> ctx, String soundId, float volume, float pitch) {
        return setSound(ctx, soundId, volume, pitch);
    }

    private static int setSound(CommandContext<CommandSourceStack> ctx, String soundId, float volume, float pitch) {
        ModConfig.getInstance().setTimerSound(soundId, volume, pitch);

        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.sound.success", soundId, volume, pitch), true);
        return 1;
    }

    private static int setScale(CommandContext<CommandSourceStack> ctx, float scale) {
        ModConfig.getInstance().setTimerScale(scale);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.scale.success", scale), true);
        return 1;
    }

    private static int viewTimerCommand(CommandContext<CommandSourceStack> ctx) {
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

    private static int updateTimerCommand(CommandContext<CommandSourceStack> ctx, String command) {
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

    private static int toggleRepeatInfinite(CommandContext<CommandSourceStack> ctx) {
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

    private static int setRepeatCount(CommandContext<CommandSourceStack> ctx, int count, int cooldownSeconds) {
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

    private static int viewSequence(CommandContext<CommandSourceStack> ctx) {
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

    private static int setSequence(CommandContext<CommandSourceStack> ctx, String nextName, int cooldownSeconds) {
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

    private static int clearSequence(CommandContext<CommandSourceStack> ctx) {
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

    private static int viewCondition(CommandContext<CommandSourceStack> ctx) {
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

    private static int setCondition(CommandContext<CommandSourceStack> ctx,
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

    private static int clearCondition(CommandContext<CommandSourceStack> ctx) {
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

    private static int exportTimer(CommandContext<CommandSourceStack> ctx, String name) {
        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        if (TimerStorage.exportTimer(name, timerOpt.get())) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.export.success", name), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.translatable("ontime.command.export.failed", name));
        return 0;
    }

    private static int importTimer(CommandContext<CommandSourceStack> ctx, String filename, String overrideName) {
        if (!TimerStorage.exportFileExists(filename)) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.import.notfound", filename));
            return 0;
        }
        Timer imported = TimerStorage.importTimerFromExports(filename);
        if (imported == null) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.import.invalid", filename));
            return 0;
        }
        String targetName = (overrideName != null && !overrideName.isEmpty()) ? overrideName : imported.getName();
        if (TimerManager.getInstance().hasTimer(targetName)) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.import.exists", targetName));
            return 0;
        }
        long maxSeconds = ModConfig.getInstance().getMaxTimerSeconds();
        if (imported.getTargetTicks() / 20L > maxSeconds) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.error.maxtime", formatTime(maxSeconds)));
            return 0;
        }
        Timer toAdd = imported;
        if (overrideName != null && !overrideName.isEmpty() && !overrideName.equals(imported.getName())) {
            com.google.gson.JsonObject json = imported.toJson();
            json.addProperty("name", overrideName);
            json.addProperty("running", false);
            json.addProperty("wasRunningBeforeShutdown", false);
            toAdd = Timer.fromJson(json);
        }
        if (TimerManager.getInstance().addTimer(toAdd)) {
            final Timer added = toAdd;
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.import.success", added.getName()), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.translatable("ontime.command.import.exists", targetName));
        return 0;
    }

    private static int cloneTimer(CommandContext<CommandSourceStack> ctx, String sourceName, String destName) {
        Optional<Timer> sourceOpt = TimerManager.getInstance().getTimer(sourceName);
        if (sourceOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", sourceName));
            return 0;
        }
        if (TimerManager.getInstance().hasTimer(destName)) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.clone.exists", destName));
            return 0;
        }
        com.google.gson.JsonObject json = sourceOpt.get().toJson();
        json.addProperty("name", destName);
        json.addProperty("running", false);
        json.addProperty("wasRunningBeforeShutdown", false);
        json.addProperty("repeatsDone", 0);
        long targetTicks = json.get("targetTicks").getAsLong();
        boolean countUp = json.get("countUp").getAsBoolean();
        json.addProperty("currentTicks", countUp ? 0 : targetTicks);
        Timer cloned = Timer.fromJson(json);
        if (TimerManager.getInstance().addTimer(cloned)) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.clone.success", sourceName, destName), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.translatable("ontime.command.clone.exists", destName));
        return 0;
    }

    private static int webPanelStart(CommandContext<CommandSourceStack> ctx, int port) {
        if (TimerWebPanel.getInstance().isRunning()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.webpanel.already_running",
                    TimerWebPanel.getInstance().getAccessUrl()));
            return 0;
        }
        TimerWebPanel.getInstance().start(port, ctx.getSource().getServer());
        if (!TimerWebPanel.getInstance().isRunning()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.webpanel.start_failed", port));
            return 0;
        }
        String url = TimerWebPanel.getInstance().getAccessUrl();
        ctx.getSource().sendSuccess(() -> Component.translatable("ontime.command.webpanel.started", url), false);
        return 1;
    }

    private static int webPanelStop(CommandContext<CommandSourceStack> ctx) {
        if (!TimerWebPanel.getInstance().isRunning()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.webpanel.not_running"));
            return 0;
        }
        TimerWebPanel.getInstance().stop();
        ctx.getSource().sendSuccess(() -> Component.translatable("ontime.command.webpanel.stopped"), true);
        return 1;
    }

    private static int webPanelInfo(CommandContext<CommandSourceStack> ctx) {
        if (!TimerWebPanel.getInstance().isRunning()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("ontime.command.webpanel.not_running"), false);
        } else {
            String url = TimerWebPanel.getInstance().getAccessUrl();
            int clients = TimerWebPanel.getInstance().getConnectedClients();
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.webpanel.info", url, clients), false);
        }
        return 1;
    }

    private static int createTimerWithExpr(CommandContext<CommandSourceStack> ctx, boolean countUp) {
        String name = StringArgumentType.getString(ctx, "name");
        String expression = StringArgumentType.getString(ctx, "expression");
        OptionalLong resolved = ExpressionEvaluator.evaluate(expression, ctx.getSource().getServer());
        if (resolved.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.expr.invalid", expression));
            return 0;
        }
        long totalSeconds = resolved.getAsLong();
        long maxSeconds = ModConfig.getInstance().getMaxTimerSeconds();
        if (totalSeconds > maxSeconds) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.error.maxtime", formatTime(maxSeconds)));
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

    private static int setTimerExpr(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        String expression = StringArgumentType.getString(ctx, "expression");
        OptionalLong resolved = ExpressionEvaluator.evaluate(expression, ctx.getSource().getServer());
        if (resolved.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.expr.invalid", expression));
            return 0;
        }
        long totalSeconds = resolved.getAsLong();
        long maxSeconds = ModConfig.getInstance().getMaxTimerSeconds();
        if (totalSeconds > maxSeconds) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.error.maxtime", formatTime(maxSeconds)));
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

    private static int addTimerExpr(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        String expression = StringArgumentType.getString(ctx, "expression");
        OptionalLong resolved = ExpressionEvaluator.evaluate(expression, ctx.getSource().getServer());
        if (resolved.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.expr.invalid", expression));
            return 0;
        }
        long addSeconds = resolved.getAsLong();
        long maxSeconds = ModConfig.getInstance().getMaxTimerSeconds();

        Optional<Timer> timerOpt = TimerManager.getInstance().getTimer(name);
        if (timerOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }

        long currentSeconds = timerOpt.get().getCurrentTicks() / 20L;
        long newTotalSeconds = currentSeconds + addSeconds;
        if (newTotalSeconds > maxSeconds) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.error.maxtime.add", formatTime(maxSeconds)));
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

}