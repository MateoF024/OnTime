package com.mateof24.admin;

import com.mateof24.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Who currently has the admin panel open.
 *
 * <p>The panel state is pushed rather than polled, and only to the players who
 * asked for it. Broadcasting it would mean every client on the server receiving
 * the full timer configuration once a second — data most of them have no
 * business seeing and nobody asked for.</p>
 *
 * <p>Subscribing grants nothing. It is a delivery list, not a permission: every
 * action is authorised on its own when it arrives.</p>
 */
public final class AdminSubscriptions {

    private AdminSubscriptions() {}

    /** Heartbeat while a panel is open, in server ticks. */
    private static final int PUSH_INTERVAL = 20;

    private static final Set<UUID> subscribers = new LinkedHashSet<>();
    private static int sinceLastPush = 0;
    private static boolean dirty = false;

    public static void subscribe(ServerPlayer player) {
        subscribers.add(player.getUUID());
        dirty = true;
    }

    public static void unsubscribe(UUID player) {
        subscribers.remove(player);
    }

    public static boolean isSubscribed(UUID player) {
        return subscribers.contains(player);
    }

    public static boolean any() {
        return !subscribers.isEmpty();
    }

    /** Requests a push on the next tick. Cheap and idempotent. */
    public static void markDirty() {
        dirty = true;
    }

    /**
     * Pushes the state to every open panel, at most once per tick.
     *
     * <p>Called every server tick; does nothing at all while no panel is open,
     * which is the normal case and has to cost nothing.</p>
     */
    public static void tick(MinecraftServer server) {
        if (subscribers.isEmpty()) {
            sinceLastPush = 0;
            dirty = false;
            return;
        }

        sinceLastPush++;
        if (!dirty && sinceLastPush < PUSH_INTERVAL) return;

        sinceLastPush = 0;
        dirty = false;
        push(server);
    }

    /**
     * Builds the snapshot once and sends the same string to everyone.
     *
     * <p>The state does not depend on who is looking — it is server
     * configuration, and everyone who may see it may see all of it.</p>
     */
    private static void push(MinecraftServer server) {
        String json = AdminOps.state(server).toString();

        // Copy: a player who left is dropped from the set while iterating.
        for (UUID id : Set.copyOf(subscribers)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                subscribers.remove(id);
                continue;
            }
            // Losing the permission mid-session closes the panel, rather than
            // keeping a live feed open for someone who is no longer an admin.
            if (!AdminOps.Caller.of(player).allowed()) {
                subscribers.remove(id);
                continue;
            }
            Services.PLATFORM.sendAdminState(player, json);
        }
    }
}
