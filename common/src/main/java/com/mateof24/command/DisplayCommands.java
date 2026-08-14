package com.mateof24.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mateof24.config.ModConfig;
import com.mateof24.config.TimerPositionPreset;
import com.mateof24.manager.DisplaySlots;
import com.mateof24.manager.TimerManager;
import com.mateof24.platform.Services;
import com.mateof24.storage.PlayerPreferences;
import com.mateof24.timer.Timer;
import com.mateof24.timer.TimerRun;
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

    /** Hands a timer's setting back to whatever the server default is now. */
    private static final String RESET = "reset";

    /** {@code /timer position <timer>}. */
    static int positionView(CommandContext<CommandSourceStack> ctx) {
        String first = StringArgumentType.getString(ctx, "name");
        if (!TimerManager.getInstance().hasTimer(first)) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", first));
            return 0;
        }
        return showTimerPosition(ctx, first);
    }

    /** {@code /timer position <timer> <preset|reset>}. */
    static int positionSet(CommandContext<CommandSourceStack> ctx) {
        String first = StringArgumentType.getString(ctx, "name");
        String second = StringArgumentType.getString(ctx, "preset");

        Timer timer = TimerManager.getInstance().getTimer(first).orElse(null);
        if (timer == null) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", first));
            return 0;
        }

        if (RESET.equalsIgnoreCase(second)) {
            ModConfig config = ModConfig.getInstance();
            String fresh = config.getPositionPreset().name();
            if (!checkSlot(ctx, timer, fresh)) return 0;
            timer.display().setPreset(fresh);
            timer.display().setX(config.getTimerX());
            timer.display().setY(config.getTimerY());
            TimerManager.getInstance().saveTimer(timer);
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.position.reset", first), true);
            com.mateof24.network.TimerState.markDirty();
            return 1;
        }

        TimerPositionPreset preset = TimerPositionPreset.parse(second);
        if (preset == null) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.display.unknown", second));
            return 0;
        }
        if (!checkSlot(ctx, timer, preset.name())) return 0;

        timer.display().setPreset(preset.name());
        TimerManager.getInstance().saveTimer(timer);
        ctx.getSource().sendSuccess(() -> Component.translatable("ontime.command.position.timer",
                first, preset.getDisplayName()), true);
        noteActionBar(ctx, preset);
        com.mateof24.network.TimerState.markDirty();
        return 1;
    }

    /**
     * {@code /timer position <timer> custom <x> <y>} — the coordinates
     * that make CUSTOM mean anything per timer. Without it a timer set to
     * CUSTOM would inherit the global coordinates and land on top of every
     * other CUSTOM timer, which is the one arrangement the slot rule allows.
     */
    static int positionCustom(CommandContext<CommandSourceStack> ctx, int x, int y) {
        String first = StringArgumentType.getString(ctx, "name");
        String preset = TimerPositionPreset.CUSTOM.name();

        // Coordinates only mean something for CUSTOM; every other preset
        // computes its own anchor from the screen size.
        if (TimerPositionPreset.parse(StringArgumentType.getString(ctx, "preset")) != TimerPositionPreset.CUSTOM) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.position.coords_custom_only"));
            return 0;
        }

        Timer timer = TimerManager.getInstance().getTimer(first).orElse(null);
        if (timer == null) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", first));
            return 0;
        }

        timer.display().setPreset(preset);
        timer.display().setX(x);
        timer.display().setY(y);
        TimerManager.getInstance().saveTimer(timer);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.position.custom", first, x, y), true);
        com.mateof24.network.TimerState.markDirty();
        return 1;
    }

    /**
     * Says out loud what the action bar costs. It is the one preset that is not
     * ours alone: vanilla writes there too and wins for as long as its message
     * is up, which surprises everyone exactly once.
     */
    private static void noteActionBar(CommandContext<CommandSourceStack> ctx, TimerPositionPreset preset) {
        if (preset != TimerPositionPreset.ACTIONBAR) return;
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.position.actionbar_note"), false);
    }

    private static int showTimerPosition(CommandContext<CommandSourceStack> ctx, String name) {
        Timer timer = TimerManager.getInstance().getTimer(name).orElseThrow();
        String preset = DisplaySlots.presetOf(timer);
        ctx.getSource().sendSuccess(() -> Component.translatable("ontime.command.position.view",
                name, TimerPositionPreset.valueOf(preset).getDisplayName()), false);
        return 1;
    }

    /**
     * Whether this timer may take that slot.
     *
     * <p>Only its executions in flight can collide — a definition sitting on
     * disk draws nothing — so a timer that is not running is always free to be
     * repositioned.</p>
     */
    private static boolean checkSlot(CommandContext<CommandSourceStack> ctx, Timer timer, String preset) {
        for (TimerRun run : TimerManager.getInstance().findRuns(timer.getName(), null)) {
            TimerRun other = DisplaySlots.occupant(preset, run.audience(), timer.getName());
            if (other != null) {
                reportConflict(ctx.getSource(), timer.getName(), run.audience(), preset, other);
                return false;
            }
        }
        return true;
    }

    /** Names the timer in the way, who overlaps, and where there is still room. */
    static void reportConflict(CommandSourceStack source, String timerName,
                               com.mateof24.api.Audience audience, String preset, TimerRun other) {
        source.sendFailure(Component.translatable("ontime.command.slot.conflict",
                timerName, preset.toLowerCase(java.util.Locale.ROOT), other.timerName()));

        var shared = DisplaySlots.sharedViewers(audience, other.audience());
        Component who;
        if (shared == null) {
            who = Component.translatable("ontime.audience.global");
        } else {
            java.util.List<String> names = new java.util.ArrayList<>();
            var server = source.getServer();
            for (UUID id : shared) {
                var player = server.getPlayerList().getPlayer(id);
                names.add(player != null ? player.getName().getString() : id.toString().substring(0, 8));
            }
            who = Component.literal(String.join(", ", names));
        }
        source.sendFailure(Component.translatable("ontime.command.slot.players", who));

        java.util.List<String> free = DisplaySlots.freeSlots(audience, timerName);
        source.sendFailure(Component.translatable("ontime.command.slot.free",
                free.isEmpty() ? "-" : String.join(", ", free)));
    }

    /** {@code /timer sound <timer>}. */
    static int soundView(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Timer timer = TimerManager.getInstance().getTimer(name).orElse(null);
        if (timer == null) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("ontime.command.sound.view",
                name, timer.display().soundId(),
                timer.display().soundVolume(), timer.display().soundPitch()), false);
        return 1;
    }

    /** {@code /timer sound <timer> <soundId> [volume] [pitch]}. */
    static int setSound(CommandContext<CommandSourceStack> ctx, String soundId, float volume, float pitch) {
        String name = StringArgumentType.getString(ctx, "name");
        Timer timer = TimerManager.getInstance().getTimer(name).orElse(null);
        if (timer == null) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        timer.display().setSoundId(soundId);
        timer.display().setSoundVolume(volume);
        timer.display().setSoundPitch(pitch);
        TimerManager.getInstance().saveTimer(timer);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.sound.timer", name, soundId, volume, pitch), true);
        com.mateof24.network.TimerState.markDirty();
        return 1;
    }

    /** {@code /timer sound <timer> reset} — back to the server default. */
    static int soundReset(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Timer timer = TimerManager.getInstance().getTimer(name).orElse(null);
        if (timer == null) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", name));
            return 0;
        }
        ModConfig config = ModConfig.getInstance();
        timer.display().setSoundId(config.getTimerSoundId());
        timer.display().setSoundVolume(config.getTimerSoundVolume());
        timer.display().setSoundPitch(config.getTimerSoundPitch());
        TimerManager.getInstance().saveTimer(timer);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.sound.reset", name), true);
        com.mateof24.network.TimerState.markDirty();
        return 1;
    }

    /** {@code /timer scale <timer>}. */
    static int scaleView(CommandContext<CommandSourceStack> ctx) {
        String first = StringArgumentType.getString(ctx, "name");
        Timer timer = TimerManager.getInstance().getTimer(first).orElse(null);
        if (timer == null) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", first));
            return 0;
        }
        float value = timer.display().scale();
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.scale.view", first, value), false);
        return 1;
    }

    /** {@code /timer scale <timer> <value>} — the range is the parser's job. */
    static int scaleSet(CommandContext<CommandSourceStack> ctx) {
        String first = StringArgumentType.getString(ctx, "name");
        Timer timer = TimerManager.getInstance().getTimer(first).orElse(null);
        if (timer == null) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", first));
            return 0;
        }
        timer.display().setScale(FloatArgumentType.getFloat(ctx, "value"));
        TimerManager.getInstance().saveTimer(timer);
        final float applied = timer.display().scale();
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.scale.timer", first, applied), true);
        com.mateof24.network.TimerState.markDirty();
        return 1;
    }

    /** {@code /timer scale <timer> reset} — back to the server default. */
    static int scaleReset(CommandContext<CommandSourceStack> ctx) {
        String first = StringArgumentType.getString(ctx, "name");
        Timer timer = TimerManager.getInstance().getTimer(first).orElse(null);
        if (timer == null) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", first));
            return 0;
        }
        timer.display().setScale(ModConfig.getInstance().getTimerScale());
        TimerManager.getInstance().saveTimer(timer);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.scale.reset", first), true);
        com.mateof24.network.TimerState.markDirty();
        return 1;
    }

    /**
     * Applies the tick-sound preference to the selected players.
     *
     * @param silent {@code null} toggles each player independently;
     *               {@code TRUE}/{@code FALSE} forces the same result for all.
     *               The report counts both outcomes, because a toggle over a
     *               mixed selection genuinely produces both — the old message
     *               reported whatever the last player of the loop happened to
     *               get, which was simply wrong.
     */
    static int applySilentTargets(CommandContext<CommandSourceStack> ctx, Boolean silent) {
        try {
            var targets = EntityArgument.getPlayers(ctx, "targets");
            int muted = 0, unmuted = 0;

            for (net.minecraft.server.level.ServerPlayer target : targets) {
                UUID playerUUID = target.getUUID();
                boolean newSilent = silent != null ? silent : !PlayerPreferences.getTimerSilent(playerUUID);

                PlayerPreferences.setTimerSilent(playerUUID, newSilent);
                Services.PLATFORM.sendSilentPacket(target, newSilent);
                if (newSilent) muted++; else unmuted++;
            }

            int finalMuted = muted, finalUnmuted = unmuted;
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.silent.result", finalUnmuted, finalMuted), true);
            return muted + unmuted;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.invalid_selector"));
            return 0;
        }
    }

    /**
     * Applies the timer visibility to the selected players.
     *
     * @param visible {@code null} toggles each player independently;
     *                {@code TRUE}/{@code FALSE} forces the same result for all.
     *                See {@link #applySilentTargets} for why both outcomes are
     *                counted.
     */
    static int applyHideTargets(CommandContext<CommandSourceStack> ctx, Boolean visible) {
        try {
            var targets = EntityArgument.getPlayers(ctx, "targets");
            int shown = 0, hidden = 0;

            for (net.minecraft.server.level.ServerPlayer target : targets) {
                UUID playerUUID = target.getUUID();
                boolean newVisibility = visible != null ? visible : !PlayerPreferences.getTimerVisibility(playerUUID);

                PlayerPreferences.setTimerVisibility(playerUUID, newVisibility);
                Services.PLATFORM.sendVisibilityPacket(target, newVisibility);
                if (newVisibility) shown++; else hidden++;
            }

            int finalShown = shown, finalHidden = hidden;
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.hide.result", finalShown, finalHidden), true);
            return shown + hidden;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.invalid_selector"));
            return 0;
        }
    }
}
