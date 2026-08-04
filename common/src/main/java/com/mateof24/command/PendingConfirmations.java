package com.mateof24.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * The one operation each player has staged and not yet confirmed.
 *
 * <p>A command that is about to do something big enough to be worth a second
 * look parks itself here instead of running, and {@code /timer confirm} runs
 * it. Nothing is remembered about <em>what</em> the operation is: it is a
 * closure over the already-validated arguments, so confirming cannot re-resolve
 * a selector into a different set of players than the one that was described.</p>
 *
 * <p>Only players stage. The console, command blocks, functions and datapacks
 * run straight through — a command block cannot type {@code /timer confirm},
 * and silently refusing to act would break automation that works today.</p>
 */
public final class PendingConfirmations {

    private PendingConfirmations() {}

    /** How long a staged operation stays valid. */
    static final long TIMEOUT_MILLIS = 30_000L;

    static int timeoutSeconds() {
        return (int) (TIMEOUT_MILLIS / 1000L);
    }

    private record Pending(ToIntFunction<CommandSourceStack> action, long expiresAt) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    /** The player behind a source, or null when it is not a player. */
    static ServerPlayer issuer(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player : null;
    }

    /**
     * Whether an operation creating {@code runsToCreate} executions has to be
     * confirmed first.
     *
     * <p>The threshold counts executions, not selected players, which is why
     * {@code @a} in shared mode never asks however many people are online: it
     * is one execution, exactly as cheap as the single timer of 4.0.0.</p>
     */
    static boolean required(CommandSourceStack source, int runsToCreate) {
        if (issuer(source) == null) return false;
        int threshold = com.mateof24.config.ModConfig.getInstance().getConfirmRunThreshold();
        if (threshold < 0) return false;
        return runsToCreate >= threshold;
    }

    /** Stages an operation for this player, replacing anything already staged. */
    static void stage(ServerPlayer player, ToIntFunction<CommandSourceStack> action) {
        PENDING.put(player.getUUID(), new Pending(action, System.currentTimeMillis() + TIMEOUT_MILLIS));
    }

    /**
     * Runs whatever this player staged.
     *
     * @return the operation's own result, or 0 when there was nothing staged
     *         or it had expired — the failure is reported to the source
     */
    static int confirm(CommandSourceStack source) {
        ServerPlayer player = issuer(source);
        if (player == null) {
            source.sendFailure(Component.translatable("ontime.command.confirm.player_only"));
            return 0;
        }

        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null) {
            source.sendFailure(Component.translatable("ontime.command.confirm.none"));
            return 0;
        }
        if (System.currentTimeMillis() > pending.expiresAt()) {
            source.sendFailure(Component.translatable("ontime.command.confirm.expired"));
            return 0;
        }
        return pending.action().applyAsInt(source);
    }

    /**
     * Drops expired entries. Called on the periodic server cadence so a player
     * who stages something and never confirms — or disconnects — does not
     * leave the closure, and the players it captured, alive indefinitely.
     */
    public static void sweep() {
        if (PENDING.isEmpty()) return;
        long now = System.currentTimeMillis();
        PENDING.values().removeIf(pending -> now > pending.expiresAt());
    }
}
