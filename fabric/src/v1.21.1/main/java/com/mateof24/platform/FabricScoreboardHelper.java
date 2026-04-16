package com.mateof24.platform;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.network.chat.Component;

public class FabricScoreboardHelper {

    private static final String OBJECTIVE_NAME = "ontime_active";

    public static boolean checkScoreboardCondition(MinecraftServer server, String objectiveName, int score, String target) {
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(objectiveName);
        if (objective == null) return false;

        if ("*".equals(target)) {
            for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
                ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(player, objective);
                if (info != null && info.value() >= score) return true;
            }
            return false;
        }

        ScoreHolder holder = ScoreHolder.forNameOnly(target);
        ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(holder, objective);
        return info != null && info.value() >= score;
    }

    public static void updateScoreboardTimer(MinecraftServer server, String timerName, long currentSeconds, long targetSeconds) {
        Scoreboard sb = server.getScoreboard();
        Objective obj = sb.getObjective(OBJECTIVE_NAME);
        if (obj == null) {
            obj = sb.addObjective(OBJECTIVE_NAME,
                    ObjectiveCriteria.DUMMY,
                    Component.literal("OnTime"),
                    ObjectiveCriteria.RenderType.INTEGER,
                    true, null);
        }
        ScoreHolder holder = ScoreHolder.forNameOnly(timerName);
        sb.getOrCreatePlayerScore(holder, obj).set((int) currentSeconds);
    }

    public static void clearScoreboardTimer(MinecraftServer server) {
        Scoreboard sb = server.getScoreboard();
        Objective obj = sb.getObjective(OBJECTIVE_NAME);
        if (obj != null) sb.removeObjective(obj);
    }

    public static long getScoreboardValue(MinecraftServer server, String objectiveName, String holderName) {
        Scoreboard sb = server.getScoreboard();
        Objective obj = sb.getObjective(objectiveName);
        if (obj == null) return 0;
        ScoreHolder holder = ScoreHolder.forNameOnly(holderName);
        ReadOnlyScoreInfo info = sb.getPlayerScoreInfo(holder, obj);
        return info != null ? info.value() : 0;
    }
}