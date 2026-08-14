package com.mateof24.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * {@code {"op": "...", "args": {...}}}, validated server-side.
 *
 * <p>The mod's <b>first C2S channel</b>. Everything before this was the server
 * telling clients what to draw; this is a client asking the server to do
 * something, so it is the first packet an attacker can forge. The handler
 * gives the whole decision to {@link com.mateof24.admin.AdminHandler}, which
 * re-checks the permission and every argument — having the screen open proves
 * nothing.</p>
 */
public class AdminActionPayload {

    /**
     * Deliberately small: an action is a handful of fields, and a client should
     * not be able to make the server allocate a megabyte by saying it will.
     */
    private static final int MAX_ACTION = 8192;

    private final String json;

    public AdminActionPayload(String json) {
        this.json = json;
    }

    public static void encode(AdminActionPayload msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.json, MAX_ACTION);
    }

    public static AdminActionPayload decode(FriendlyByteBuf buf) {
        return new AdminActionPayload(buf.readUtf(MAX_ACTION));
    }

    public static void handle(AdminActionPayload msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player == null || player.getServer() == null) return;
            com.google.gson.JsonObject request;
            try {
                request = com.google.gson.JsonParser.parseString(msg.json).getAsJsonObject();
            } catch (Exception e) {
                return;
            }
            com.mateof24.admin.AdminHandler.handle(player.getServer(), player, request);
        });
        context.setPacketHandled(true);
    }

    public String json() { return json; }
}
