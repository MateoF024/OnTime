package com.mateof24.command;

import com.mateof24.config.ModConfig;
import com.mateof24.config.TimerPositionPreset;
import com.mateof24.platform.Services;
import com.mateof24.storage.PlayerPreferences;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Handlers for the display subcommands:
 * position / sound / scale / hide / silent.
 * The command tree itself is registered by {@link TimerCommands}.
 */
final class DisplayCommands {

    private DisplayCommands() {}

    static int setPosition(CommandContext<CommandSourceStack> ctx) {
        String presetName = StringArgumentType.getString(ctx, "preset");
        TimerPositionPreset preset = TimerPositionPreset.fromString(presetName);

        ModConfig.getInstance().setPositionPreset(preset);

        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.position.success", preset.getDisplayName()), true);
        return 1;
    }

    static int setSoundDefault(CommandContext<CommandSourceStack> ctx, String soundId) {
        return setSound(ctx, soundId, 0.75f, 2.0f);
    }

    static int setSoundWithVolume(CommandContext<CommandSourceStack> ctx, String soundId, float volume) {
        return setSound(ctx, soundId, volume, 2.0f);
    }

    static int setSoundFull(CommandContext<CommandSourceStack> ctx, String soundId, float volume, float pitch) {
        return setSound(ctx, soundId, volume, pitch);
    }

    private static int setSound(CommandContext<CommandSourceStack> ctx, String soundId, float volume, float pitch) {
        ModConfig.getInstance().setTimerSound(soundId, volume, pitch);

        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.sound.success", soundId, volume, pitch), true);
        return 1;
    }

    static int setScale(CommandContext<CommandSourceStack> ctx, float scale) {
        ModConfig.getInstance().setTimerScale(scale);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.scale.success", scale), true);
        return 1;
    }

    static int toggleSilentSelf(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.players_only"));
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

    static int toggleSilentTargets(CommandContext<CommandSourceStack> ctx) {
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
            ctx.getSource().sendFailure(Component.translatable("ontime.command.invalid_selector"));
            return 0;
        }
    }

    static int toggleHideSelf(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.players_only"));
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

    static int toggleHideTargets(CommandContext<CommandSourceStack> ctx) {
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
            ctx.getSource().sendFailure(Component.translatable("ontime.command.invalid_selector"));
            return 0;
        }
    }
}
