package com.mateof24.platform;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.nio.file.Path;

public interface IPlatformHelper {

    String getPlatformName();

    boolean isModLoaded(String modId);

    Path getConfigDir();

    void sendTimerSyncPacket(MinecraftServer server, String name, long currentTicks,
                             long targetTicks, boolean countUp, boolean running, boolean silent);

    void sendVisibilityPacket(ServerPlayer player, boolean visible);

    void sendSilentPacket(ServerPlayer player, boolean silent);

    void sendPositionPacket(ServerPlayer player, String presetName);

    void sendPositionPacketToAll(MinecraftServer server, String presetName);

    void sendSoundPacket(ServerPlayer player, String soundId, float volume, float pitch);

    void sendSoundPacketToAll(MinecraftServer server, String soundId, float volume, float pitch);

    void registerPackets();
}