package com.mateof24.command;

import com.mateof24.config.ModConfig;
import com.mateof24.manager.TimerManager;
import com.mateof24.storage.TimerStorage;
import com.mateof24.timer.Timer;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * Handlers for the sharing subcommands: export / import / clone.
 * The command tree itself is registered by {@link TimerCommands}.
 */
final class SharingCommands {

    private SharingCommands() {}

    static int exportTimer(CommandContext<CommandSourceStack> ctx, String name) {
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

    static int importTimer(CommandContext<CommandSourceStack> ctx, String filename, String overrideName) {
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
            ctx.getSource().sendFailure(Component.translatable("ontime.command.error.maxtime", TimerCommands.formatTime(maxSeconds)));
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

    static int cloneTimer(CommandContext<CommandSourceStack> ctx, String sourceName, String destName) {
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
}
