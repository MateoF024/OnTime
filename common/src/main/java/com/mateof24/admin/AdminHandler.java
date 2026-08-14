package com.mateof24.admin;

import com.google.gson.JsonObject;
import com.mateof24.platform.Services;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * What the loaders call when an {@code admin_action} arrives.
 *
 * <p>Both loaders decode the payload their own way and then land here, so the
 * rules about what an action may do live once. Already on the server thread by
 * the time this runs.</p>
 */
public final class AdminHandler {

    private AdminHandler() {}

    /**
     * Runs one action for one player.
     *
     * <p>Rejections are answered but not explained beyond the operation's own
     * message: a client that is not allowed to be here learns only that it was
     * refused.</p>
     */
    public static void handle(MinecraftServer server, ServerPlayer player, JsonObject request) {
        if (server == null || player == null || request == null) return;

        AdminOps.Caller caller = AdminOps.Caller.of(player);
        if (!caller.allowed()) {
            com.mateof24.OnTimeConstants.LOGGER.warn(
                    "[OnTime/Admin] {} sent an admin action without permission", player.getName().getString());
            AdminSubscriptions.unsubscribe(player.getUUID());
            return;
        }

        String op = request.has("op") ? request.get("op").getAsString() : null;

        // A panel opening is an action like any other, so it goes through the
        // same permission check rather than having a door of its own.
        if ("panel.open".equals(op)) {
            AdminSubscriptions.subscribe(player);
            return;
        }
        if ("panel.close".equals(op)) {
            AdminSubscriptions.unsubscribe(player.getUUID());
            return;
        }

        JsonObject args = request.has("args") && request.get("args").isJsonObject()
                ? request.getAsJsonObject("args")
                : new JsonObject();

        AdminOps.Result result = AdminOps.apply(server, caller, op, args);

        if (result.message() != null) {
            Component message = Component.literal(result.message());
            if (result.success()) player.sendSystemMessage(message);
            else player.sendSystemMessage(message.copy().withStyle(net.minecraft.ChatFormatting.RED));
        }
        if (result.stateChanged()) {
            AdminSubscriptions.markDirty();
            com.mateof24.network.TimerState.markDirty();
        }
    }

    /**
     * Opens the panel for a player who ran {@code /timer gui}.
     *
     * <p>This snapshot is flagged {@code open}; the heartbeats that follow are
     * not. Without the distinction the client could not tell "you asked for
     * this" from "here is the state again", and closing the panel would fight
     * the next push a second later.</p>
     */
    public static void open(MinecraftServer server, ServerPlayer player) {
        AdminSubscriptions.subscribe(player);
        JsonObject state = AdminOps.state(server);
        state.addProperty("open", true);
        Services.PLATFORM.sendAdminState(player, state.toString());
    }
}
