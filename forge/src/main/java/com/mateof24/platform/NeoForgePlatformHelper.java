package com.mateof24.platform;

import com.mateof24.network.NetworkHandler;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

public class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "NeoForge";
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
    public void registerPackets() {

    }
}