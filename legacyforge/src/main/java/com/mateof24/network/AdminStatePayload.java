package com.mateof24.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The admin panel snapshot, as JSON, for one subscribed player. */
public class AdminStatePayload {

    /** Generous: it carries every timer. */
    private static final int MAX_JSON = 1 << 20;

    private final String json;

    public AdminStatePayload(String json) {
        this.json = json;
    }

    public static void encode(AdminStatePayload msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.json, MAX_JSON);
    }

    public static AdminStatePayload decode(FriendlyByteBuf buf) {
        return new AdminStatePayload(buf.readUtf(MAX_JSON));
    }

    public static void handle(AdminStatePayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> com.mateof24.gui.AdminClientState.accept(msg.json));
        ctx.get().setPacketHandled(true);
    }

    public String json() { return json; }
}
