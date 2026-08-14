package com.mateof24.platform;

import com.mateof24.network.NetworkHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

public class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() { return "Fabric"; }

    @Override
    public boolean isModLoaded(String modId) { return FabricLoader.getInstance().isModLoaded(modId); }

    @Override
    public Path getConfigDir() { return FabricLoader.getInstance().getConfigDir(); }

    @Override
    public void sendTimerState(MinecraftServer server) {
        NetworkHandler.sendTimerState(server);
    }

    @Override
    public void sendTimerState(ServerPlayer player) {
        NetworkHandler.sendTimerState(player);
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
    public void sendAdminState(ServerPlayer player, String json) {
        NetworkHandler.sendAdminState(player, json);
    }

    @Override
    public void registerPackets() { NetworkHandler.registerPackets(); }

    @Override
    public boolean checkScoreboardCondition(MinecraftServer server, String objectiveName, int score, String target) {
        return ScoreboardHelper.checkScoreboardCondition(server, objectiveName, score, target);
    }

    @Override
    public void updateScoreboardTimer(MinecraftServer server, String timerName, long currentSeconds, long targetSeconds) {
        ScoreboardHelper.updateScoreboardTimer(server, timerName, currentSeconds, targetSeconds);
    }

    @Override
    public void clearScoreboardTimer(MinecraftServer server) {
        ScoreboardHelper.clearScoreboardTimer(server);
    }

    @Override
    public long getScoreboardValue(MinecraftServer server, String objective, String holder) {
        return ScoreboardHelper.getScoreboardValue(server, objective, holder);
    }
}