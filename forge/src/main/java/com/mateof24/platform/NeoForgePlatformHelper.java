package com.mateof24.platform;

import com.mateof24.config.ModConfig;
import com.mateof24.network.NetworkHandler;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

public class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() { return "NeoForge"; }

    @Override
    public boolean isModLoaded(String modId) { return ModList.get().isLoaded(modId); }

    @Override
    public Path getConfigDir() { return FMLPaths.CONFIGDIR.get(); }

    @Override
    public void sendTimerSyncPacket(MinecraftServer server, String name, long currentTicks,
                                    long targetTicks, boolean countUp, boolean running, boolean silent) {
        NetworkHandler.syncTimerToClients(server, name, currentTicks, targetTicks, countUp, running, silent);
    }

    @Override
    public void sendVisibilityPacket(ServerPlayer player, boolean visible) {
        NetworkHandler.syncVisibilityToClient(player, visible);
    }

    @Override
    public void sendSilentPacket(ServerPlayer player, boolean silent) {
        NetworkHandler.syncSilentToClient(player, silent);
    }

    @Override
    public void sendPositionPacket(ServerPlayer player, String presetName) {}

    @Override
    public void sendPositionPacketToAll(MinecraftServer server, String presetName) {}

    @Override
    public void sendDisplayConfigPacket(ServerPlayer player) {
        NetworkHandler.syncDisplayConfigToClient(player, ModConfig.getInstance());
    }

    @Override
    public void sendDisplayConfigPacketToAll(MinecraftServer server) {
        NetworkHandler.syncDisplayConfigToAllClients(server, ModConfig.getInstance());
    }

    @Override
    public void registerPackets() {}
}