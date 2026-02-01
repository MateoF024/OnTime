package com.mateof24.command;

import com.mateof24.config.TimerPositionPreset;
import com.mateof24.platform.Services;
import com.mateof24.config.ModConfig;
import com.mateof24.manager.TimerManager;
import com.mateof24.storage.PlayerPreferences;
import com.mateof24.timer.Timer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityArgument;
import com.mateof24.command.HelpSystem;

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
        dispatcher.register(Commands.literal("timer")
                .requires(source -> source.hasPermission(ModConfig.getInstance().getRequiredPermissionLevel()))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
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
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
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
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(TimerCommands::startTimer)
                        )
                )
                .then(Commands.literal("pause")
                        .executes(TimerCommands::pauseTimer)
                )
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(TimerCommands::removeTimer)
                        )
                )
                .then(Commands.literal("add")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
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
                        .requires(source -> {
                            // Si allowPlayersUseList es true, todos pueden usar
                            // Si es false, solo usuarios con permiso pueden usar
                            if (ModConfig.getInstance().getAllowPlayersUseList()) {
                                return true;
                            }
                            return source.hasPermission(ModConfig.getInstance().getRequiredPermissionLevel());
                        })
                        .executes(TimerCommands::listTimers)
                )
                .then(Commands.literal("silent")
                        .requires(source -> {
                            // Si allowPlayersUseSilent es true, todos pueden usar
                            // Si es false, solo usuarios con permiso pueden usar
                            if (ModConfig.getInstance().getAllowPlayersUseSilent()) {
                                return true;
                            }
                            return source.hasPermission(ModConfig.getInstance().getRequiredPermissionLevel());
                        })
                        .executes(TimerCommands::toggleSilentSelf)
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(TimerCommands::toggleSilentTargets)
                        )
                )
                .then(Commands.literal("hide")
                        .requires(source -> {
                            // Si allowPlayersUseHide es true, todos pueden usar
                            // Si es false, solo usuarios con permiso pueden usar
                            if (ModConfig.getInstance().getAllowPlayersUseHide()) {
                                return true;
                            }
                            return source.hasPermission(ModConfig.getInstance().getRequiredPermissionLevel());
                        })
                        .executes(TimerCommands::toggleHideSelf)
                        .then(Commands.argument("targets", net.minecraft.commands.arguments.EntityArgument.players())
                                .executes(TimerCommands::toggleHideTargets)
                        )
                )
                .then(Commands.literal("stop")
                        .executes(TimerCommands::stopTimer)
                )
                .then(Commands.literal("reset")
                        .executes(TimerCommands::resetCurrentTimer)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(TimerCommands::resetNamedTimer)
                        )
                )
                .then(Commands.literal("help")
                        .executes(ctx -> HelpSystem.showHelpPage(ctx.getSource(), 1))
                        .then(Commands.argument("pageOrCommand", StringArgumentType.word())
                                .executes(ctx -> {
                                    String arg = StringArgumentType.getString(ctx, "pageOrCommand");

                                    // Intentar parsear como número (página)
                                    try {
                                        int page = Integer.parseInt(arg);
                                        return HelpSystem.showHelpPage(ctx.getSource(), page);
                                    } catch (NumberFormatException e) {
                                        // No es un número, tratarlo como nombre de comando
                                        return HelpSystem.showCommandHelp(ctx.getSource(), arg);
                                    }
                                })
                        )
                )
                .then(Commands.literal("position")
                        .requires(source -> {
                            if (ModConfig.getInstance().getAllowPlayersChangePosition()) {
                                return true;
                            }
                            return source.hasPermission(ModConfig.getInstance().getRequiredPermissionLevel());
                        })
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (TimerPositionPreset preset : TimerPositionPreset.values()) {
                                        String presetName = preset.name().toLowerCase();
                                        if (presetName.startsWith(builder.getRemaining().toLowerCase())) {
                                            builder.suggest(presetName);
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(TimerCommands::setPositionSelf)
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(TimerCommands::setPositionTargets)
                                )
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
        if (activeTimer.isPresent()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.start.active", activeTimer.get().getName()));
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
            timer.setRunning(true);
            TimerManager.getInstance().saveTimers();

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

    private static int toggleSilentGlobal(CommandContext<CommandSourceStack> ctx) {
        Optional<Timer> activeTimer = TimerManager.getInstance().getActiveTimer();

        if (activeTimer.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.silent.none"));
            return 0;
        }

        Timer timer = activeTimer.get();
        timer.setSilent(!timer.isSilent());
        TimerManager.getInstance().saveTimers();

        ctx.getSource().sendSuccess(() ->
                Component.translatable(timer.isSilent() ? "ontime.command.silent.disabled" : "ontime.command.silent.enabled", timer.getName()), true);

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

        if (activeTimer.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.stop.none"));
            return 0;
        }

        Timer timer = activeTimer.get();
        timer.reset();
        TimerManager.getInstance().clearActiveTimer();
        TimerManager.getInstance().saveTimers();

        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.stop.success", timer.getName()), true);

        Services.PLATFORM.sendTimerSyncPacket(ctx.getSource().getServer(), "", 0, 0, false, false, false);
        return 1;
    }

    private static int resetCurrentTimer(CommandContext<CommandSourceStack> ctx) {
        Optional<Timer> activeTimer = TimerManager.getInstance().getActiveTimer();

        if (activeTimer.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.reset.noactive"));
            return 0;
        }

        Timer timer = activeTimer.get();
        boolean wasRunning = timer.isRunning();
        timer.reset();
        TimerManager.getInstance().saveTimers();

        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.reset.success", timer.getName()), true);

        if (wasRunning) {
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

    private static int setPositionSelf(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("§cThis command can only be used by players"));
            return 0;
        }

        String presetName = StringArgumentType.getString(ctx, "preset");
        TimerPositionPreset preset = TimerPositionPreset.fromString(presetName);

        UUID playerUUID = player.getUUID();
        PlayerPreferences.setTimerPosition(playerUUID, preset.name());
        Services.PLATFORM.sendPositionPacket(player, preset.name());

        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.position.success", preset.getDisplayName()), false);

        return 1;
    }

    private static int setPositionTargets(CommandContext<CommandSourceStack> ctx) {
        String presetName = StringArgumentType.getString(ctx, "preset");
        TimerPositionPreset preset = TimerPositionPreset.fromString(presetName);

        try {
            var targets = EntityArgument.getPlayers(ctx, "targets");
            int count = 0;

            for (net.minecraft.server.level.ServerPlayer target : targets) {
                UUID playerUUID = target.getUUID();
                PlayerPreferences.setTimerPosition(playerUUID, preset.name());
                Services.PLATFORM.sendPositionPacket(target, preset.name());
                count++;
            }

            int finalCount = count;
            String finalPresetName = preset.getDisplayName();

            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.position.success_targets",
                            finalPresetName, finalCount), true);

            return count;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("§cInvalid target selector"));
            return 0;
        }
    }
}