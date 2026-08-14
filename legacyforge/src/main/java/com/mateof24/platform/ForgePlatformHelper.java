package com.mateof24.platform;

import com.mateof24.network.NetworkHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.ModList;

import java.nio.file.Path;

/**
 * The loader seam for Forge 47, and the one class on this branch with no
 * counterpart on 'main' — there the second loader is NeoForge.
 *
 * <p>It is the same shape as the Fabric one beside it: everything to do with
 * packets goes to {@link NetworkHandler}, and everything to do with the
 * scoreboard goes to {@link ScoreboardHelper}, which is plain vanilla API and
 * common to both. What is left here is the three things only the loader can
 * answer.</p>
 */
public class ForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() { return "Forge"; }

    @Override
    public boolean isModLoaded(String modId) { return ModList.get().isLoaded(modId); }

    @Override
    public Path getConfigDir() { return FMLPaths.CONFIGDIR.get(); }

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
    public void registerPackets() { NetworkHandler.registerPackets(); }

    @Override
    public void sendAdminState(ServerPlayer player, String json) {
        NetworkHandler.sendAdminState(player, json);
    }

    @Override
    public boolean checkScoreboardCondition(MinecraftServer server, String objective, int score, String target) {
        return ScoreboardHelper.checkScoreboardCondition(server, objective, score, target);
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
