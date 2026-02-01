package com.mateof24.platform;

import com.mateof24.network.NetworkHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public class ForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Forge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

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
    public void sendPositionPacket(ServerPlayer player, String presetName) {
        NetworkHandler.syncPositionToClient(player, presetName);
    }

    @Override
    public void sendPositionPacketToAll(MinecraftServer server, String presetName) {
        NetworkHandler.syncPositionToAllClients(server, presetName);
    }

    @Override
    public void registerPackets() {
        NetworkHandler.registerPackets();
    }
}