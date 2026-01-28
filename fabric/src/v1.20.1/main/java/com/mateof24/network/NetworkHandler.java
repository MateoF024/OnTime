package com.mateof24.network;

import com.mateof24.OnTime;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public class NetworkHandler {
    public static final ResourceLocation TIMER_SYNC_ID = new ResourceLocation(OnTime.MOD_ID, "timer_sync");
    public static final ResourceLocation TIMER_VISIBILITY_ID = new ResourceLocation(OnTime.MOD_ID, "timer_visibility");

    public static void registerPackets() {
    }

    public static void syncVisibilityToClient(net.minecraft.server.level.ServerPlayer player, boolean visible) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(visible);
        ServerPlayNetworking.send(player, TIMER_VISIBILITY_ID, buf);
    }

    public static void syncTimerToClients(MinecraftServer server, String name,
                                          long currentTicks, long targetTicks, boolean countUp,
                                          boolean running, boolean silent) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(name);
        buf.writeLong(currentTicks);
        buf.writeLong(targetTicks);
        buf.writeBoolean(countUp);
        buf.writeBoolean(running);
        buf.writeBoolean(silent);
        buf.writeLong(server.getTickCount());

        for (var player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, TIMER_SYNC_ID, buf);
        }
    }
}