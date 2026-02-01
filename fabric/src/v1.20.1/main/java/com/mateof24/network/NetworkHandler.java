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
    public static final ResourceLocation TIMER_SILENT_ID = new ResourceLocation(OnTime.MOD_ID, "timer_silent");
    public static final ResourceLocation TIMER_POSITION_ID = new ResourceLocation(OnTime.MOD_ID, "timer_position");
    public static final ResourceLocation TIMER_SOUND_ID = new ResourceLocation(OnTime.MOD_ID, "timer_sound");

    public static void registerPackets() {
    }

    public static void syncVisibilityToClient(net.minecraft.server.level.ServerPlayer player, boolean visible) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(visible);
        ServerPlayNetworking.send(player, TIMER_VISIBILITY_ID, buf);
    }

    public static void syncSilentToClient(net.minecraft.server.level.ServerPlayer player, boolean silent) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(silent);
        ServerPlayNetworking.send(player, TIMER_SILENT_ID, buf);
    }

    public static void syncPositionToClient(net.minecraft.server.level.ServerPlayer player, String presetName) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(presetName);
        ServerPlayNetworking.send(player, TIMER_POSITION_ID, buf);
    }

    public static void syncPositionToAllClients(MinecraftServer server, String presetName) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(presetName);

        for (var player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, TIMER_POSITION_ID, buf);
        }
    }

    public static void syncSoundToClient(net.minecraft.server.level.ServerPlayer player, String soundId, float volume, float pitch) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(soundId);
        buf.writeFloat(volume);
        buf.writeFloat(pitch);
        ServerPlayNetworking.send(player, TIMER_SOUND_ID, buf);
    }

    public static void syncSoundToAllClients(MinecraftServer server, String soundId, float volume, float pitch) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(soundId);
        buf.writeFloat(volume);
        buf.writeFloat(pitch);

        for (var player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, TIMER_SOUND_ID, buf);
        }
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