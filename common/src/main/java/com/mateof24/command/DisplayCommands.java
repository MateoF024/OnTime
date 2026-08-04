package com.mateof24.command;

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

    /** Reserved first tokens: they win over a timer that happens to share the name. */
    private static final String DEFAULT = "default";
    private static final String CLEAR = "clear";

    /**
     * {@code /timer position <first>} — either the legacy global form
     * ({@code /timer position bossbar}) or a request to see a timer's current
     * one.
     *
     * <p>The tree cannot tell those apart, so the handler does, exactly as
     * planned: an existing timer name wins, otherwise a valid preset name means
     * the legacy global form. Nothing that parsed in 4.0.0 stops parsing —
     * which matters, because {@code /timer position bossbar} is in command
     * blocks that were placed months ago.</p>
     */
    static int position(CommandContext<CommandSourceStack> ctx) {
        String first = StringArgumentType.getString(ctx, "first");

        if (TimerManager.getInstance().hasTimer(first)) return showTimerPosition(ctx, first);

        TimerPositionPreset preset = TimerPositionPreset.parse(first);
        if (preset == null) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.display.unknown", first));
            return 0;
        }
        int result = setDefaultPosition(ctx, preset);
        if (result > 0) {
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.position.legacy_note"), false);
        }
        return result;
    }

    /** {@code /timer position <default|name> <preset|clear>}. */
    static int position2(CommandContext<CommandSourceStack> ctx) {
        String first = StringArgumentType.getString(ctx, "first");
        String second = StringArgumentType.getString(ctx, "second");

        if (DEFAULT.equalsIgnoreCase(first)) {
            TimerPositionPreset preset = TimerPositionPreset.parse(second);
            if (preset == null) {
                ctx.getSource().sendFailure(Component.translatable("ontime.command.display.unknown", second));
                return 0;
            }
            return setDefaultPosition(ctx, preset);
        }

        Timer timer = TimerManager.getInstance().getTimer(first).orElse(null);
        if (timer == null) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", first));
            return 0;
        }

        if (CLEAR.equalsIgnoreCase(second)) {
            String inherited = ModConfig.getInstance().getPositionPreset().name();
            if (!checkSlot(ctx, timer, inherited)) return 0;
            timer.setPosition(null);
            timer.setCustomPosition(null, null);
            TimerManager.getInstance().saveTimer(timer);
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.position.cleared", first), true);
            com.mateof24.network.TimerState.markDirty();
            return 1;
        }

        TimerPositionPreset preset = TimerPositionPreset.parse(second);
        if (preset == null) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.display.unknown", second));
            return 0;
        }
        if (!checkSlot(ctx, timer, preset.name())) return 0;

        timer.setPosition(preset.name());
        TimerManager.getInstance().saveTimer(timer);
        ctx.getSource().sendSuccess(() -> Component.translatable("ontime.command.position.timer",
                first, preset.getDisplayName()), true);
        noteActionBar(ctx, preset);
        com.mateof24.network.TimerState.markDirty();
        return 1;
    }

    /**
     * {@code /timer position <default|name> custom <x> <y>} — the coordinates
     * that make CUSTOM mean anything per timer. Without it a timer set to
     * CUSTOM would inherit the global coordinates and land on top of every
     * other CUSTOM timer, which is the one arrangement the slot rule allows.
     */
    static int positionCustom(CommandContext<CommandSourceStack> ctx, int x, int y) {
        String first = StringArgumentType.getString(ctx, "first");
        String preset = TimerPositionPreset.CUSTOM.name();

        // Coordinates only mean something for CUSTOM; every other preset
        // computes its own anchor from the screen size.
        if (TimerPositionPreset.parse(StringArgumentType.getString(ctx, "second")) != TimerPositionPreset.CUSTOM) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.position.coords_custom_only"));
            return 0;
        }

        if (DEFAULT.equalsIgnoreCase(first)) {
            ModConfig config = ModConfig.getInstance();
            config.setPositionPreset(TimerPositionPreset.CUSTOM);
            config.setTimerX(x);
            config.setTimerY(y);
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.position.default_custom", x, y), true);
            com.mateof24.network.TimerState.markDirty();
            return 1;
        }

        Timer timer = TimerManager.getInstance().getTimer(first).orElse(null);
        if (timer == null) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", first));
            return 0;
        }

        timer.setPosition(preset);
        timer.setCustomPosition(x, y);
        TimerManager.getInstance().saveTimer(timer);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.position.custom", first, x, y), true);
        com.mateof24.network.TimerState.markDirty();
        return 1;
    }

    private static int setDefaultPosition(CommandContext<CommandSourceStack> ctx, TimerPositionPreset preset) {
        // Runs that inherit the default all move at once, so the change can
        // collide two of them that were fine a moment ago.
        for (TimerRun run : TimerManager.getInstance().runsView()) {
            if (run.timer().getPosition() != null) continue;
            TimerRun other = DisplaySlots.occupant(preset.name(), run.audience(), run.timerName());
            if (other != null) {
                reportConflict(ctx.getSource(), run.timerName(), run.audience(), preset.name(), other);
                return 0;
            }
        }

        ModConfig.getInstance().setPositionPreset(preset);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.position.success", preset.getDisplayName()), true);
        noteActionBar(ctx, preset);
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
        boolean inherited = timer.getPosition() == null;
        ctx.getSource().sendSuccess(() -> Component.translatable(
                inherited ? "ontime.command.position.view_inherited" : "ontime.command.position.view",
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
                               com.mateof24.timer.Audience audience, String preset, TimerRun other) {
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

    /**
     * {@code /timer scale <first>} — the legacy global form when the token is a
     * number, a timer's current scale when it names one. Same handler-side
     * disambiguation as {@link #position}, and for the same reason.
     */
    static int scale(CommandContext<CommandSourceStack> ctx) {
        String first = StringArgumentType.getString(ctx, "first");

        if (TimerManager.getInstance().hasTimer(first)) {
            Timer timer = TimerManager.getInstance().getTimer(first).orElseThrow();
            float value = timer.getScale() != null ? timer.getScale() : ModConfig.getInstance().getTimerScale();
            boolean inherited = timer.getScale() == null;
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    inherited ? "ontime.command.scale.view_inherited" : "ontime.command.scale.view",
                    first, value), false);
            return 1;
        }

        Float value = parseScale(first);
        if (value == null) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.display.unknown", first));
            return 0;
        }
        ModConfig.getInstance().setTimerScale(value);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.scale.success", value), true);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.scale.legacy_note"), false);
        com.mateof24.network.TimerState.markDirty();
        return 1;
    }

    /** {@code /timer scale <default|name> <value|clear>}. */
    static int scale2(CommandContext<CommandSourceStack> ctx) {
        String first = StringArgumentType.getString(ctx, "first");
        String second = StringArgumentType.getString(ctx, "second");

        if (DEFAULT.equalsIgnoreCase(first)) {
            Float value = parseScale(second);
            if (value == null) {
                ctx.getSource().sendFailure(Component.translatable("ontime.command.display.unknown", second));
                return 0;
            }
            ModConfig.getInstance().setTimerScale(value);
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.scale.success", value), true);
            com.mateof24.network.TimerState.markDirty();
            return 1;
        }

        Timer timer = TimerManager.getInstance().getTimer(first).orElse(null);
        if (timer == null) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.notfound", first));
            return 0;
        }

        if (CLEAR.equalsIgnoreCase(second)) {
            timer.setScale(null);
            TimerManager.getInstance().saveTimer(timer);
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.scale.cleared", first), true);
            com.mateof24.network.TimerState.markDirty();
            return 1;
        }

        Float value = parseScale(second);
        if (value == null) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.display.unknown", second));
            return 0;
        }
        timer.setScale(value);
        TimerManager.getInstance().saveTimer(timer);
        final float applied = timer.getScale();
        ctx.getSource().sendSuccess(() ->
                Component.translatable("ontime.command.scale.timer", first, applied), true);
        com.mateof24.network.TimerState.markDirty();
        return 1;
    }

    /** A scale in the accepted range, or null when the token is not one. */
    private static Float parseScale(String token) {
        try {
            float value = Float.parseFloat(token);
            if (value < 0.1f || value > 5.0f) return null;
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
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
