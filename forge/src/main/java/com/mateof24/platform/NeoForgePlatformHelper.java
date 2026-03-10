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

    @Override
    public boolean checkScoreboardCondition(MinecraftServer server, String objectiveName, int score, String target) {
        net.minecraft.world.scores.Scoreboard scoreboard = server.getScoreboard();
        net.minecraft.world.scores.Objective objective = scoreboard.getObjective(objectiveName);
        if (objective == null) return false;

        if ("*".equals(target)) {
            for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
                net.minecraft.world.scores.ReadOnlyScoreInfo info =
                        scoreboard.getPlayerScoreInfo(player, objective);
                if (info != null && info.value() >= score) return true;
            }
            return false;
        }

        net.minecraft.world.scores.ScoreHolder holder =
                net.minecraft.world.scores.ScoreHolder.forNameOnly(target);
        net.minecraft.world.scores.ReadOnlyScoreInfo info =
                scoreboard.getPlayerScoreInfo(holder, objective);
        return info != null && info.value() >= score;
    }

    private static final String OBJECTIVE_NAME = "ontime_active";

    @Override
    public void updateScoreboardTimer(MinecraftServer server, String timerName, long currentSeconds, long targetSeconds) {
        net.minecraft.world.scores.Scoreboard sb = server.getScoreboard();
        net.minecraft.world.scores.Objective obj = sb.getObjective(OBJECTIVE_NAME);
        if (obj == null) {
            obj = sb.addObjective(OBJECTIVE_NAME,
                    net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,
                    net.minecraft.network.chat.Component.literal("OnTime"),
                    net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER,
                    true, null);
        }
        net.minecraft.world.scores.ScoreHolder holder =
                net.minecraft.world.scores.ScoreHolder.forNameOnly(timerName);
        sb.getOrCreatePlayerScore(holder, obj).set((int) currentSeconds);
    }

    @Override
    public void clearScoreboardTimer(MinecraftServer server) {
        net.minecraft.world.scores.Scoreboard sb = server.getScoreboard();
        if (sb.getObjective(OBJECTIVE_NAME) != null) {
            sb.removeObjective(sb.getObjective(OBJECTIVE_NAME));
        }
    }
}