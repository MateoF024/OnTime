package com.mateof24.network;

import com.mateof24.OnTime;
import com.mateof24.config.ModConfig;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class NetworkHandler {
    public static final ResourceLocation TIMER_SYNC_ID = new ResourceLocation(OnTime.MOD_ID, "timer_sync");
    public static final ResourceLocation TIMER_VISIBILITY_ID = new ResourceLocation(OnTime.MOD_ID, "timer_visibility");
    public static final ResourceLocation TIMER_SILENT_ID = new ResourceLocation(OnTime.MOD_ID, "timer_silent");
    public static final ResourceLocation TIMER_DISPLAY_CONFIG_ID = new ResourceLocation(OnTime.MOD_ID, "timer_display_config");

    public static void registerPackets() {}

    public static void syncTimerToClients(MinecraftServer server, String name, long currentTicks,
                                          long targetTicks, boolean countUp, boolean running, boolean silent) {
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

    public static void syncVisibilityToClient(ServerPlayer player, boolean visible) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(visible);
        ServerPlayNetworking.send(player, TIMER_VISIBILITY_ID, buf);
    }

    public static void syncSilentToClient(ServerPlayer player, boolean silent) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(silent);
        ServerPlayNetworking.send(player, TIMER_SILENT_ID, buf);
    }

    public static void syncDisplayConfigToClient(ServerPlayer player, ModConfig cfg) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        writeDisplayConfig(buf, cfg);
        ServerPlayNetworking.send(player, TIMER_DISPLAY_CONFIG_ID, buf);
    }

    public static void syncDisplayConfigToAllClients(MinecraftServer server, ModConfig cfg) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        writeDisplayConfig(buf, cfg);
        for (var player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, TIMER_DISPLAY_CONFIG_ID, buf);
        }
    }

    private static void writeDisplayConfig(FriendlyByteBuf buf, ModConfig cfg) {
        buf.writeInt(cfg.getTimerX());
        buf.writeInt(cfg.getTimerY());
        buf.writeUtf(cfg.getPositionPreset().name());
        buf.writeFloat(cfg.getTimerScale());
        buf.writeInt(cfg.getColorHigh());
        buf.writeInt(cfg.getColorMid());
        buf.writeInt(cfg.getColorLow());
        buf.writeInt(cfg.getThresholdMid());
        buf.writeInt(cfg.getThresholdLow());
        buf.writeUtf(cfg.getTimerSoundId());
        buf.writeFloat(cfg.getTimerSoundVolume());
        buf.writeFloat(cfg.getTimerSoundPitch());
    }
}