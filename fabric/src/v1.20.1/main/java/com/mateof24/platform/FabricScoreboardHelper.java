package com.mateof24.platform;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
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
                if (scoreboard.hasPlayerScore(player.getScoreboardName(), objective)) {
                    if (scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective).getScore() >= score)
                        return true;
                }
            }
            return false;
        }

        if (!scoreboard.hasPlayerScore(target, objective)) return false;
        return scoreboard.getOrCreatePlayerScore(target, objective).getScore() >= score;
    }

    public static void updateScoreboardTimer(MinecraftServer server, String timerName, long currentSeconds, long targetSeconds) {
        Scoreboard sb = server.getScoreboard();
        Objective obj = sb.getObjective(OBJECTIVE_NAME);
        if (obj == null) {
            obj = sb.addObjective(OBJECTIVE_NAME,
                    ObjectiveCriteria.DUMMY,
                    Component.literal("OnTime"),
                    ObjectiveCriteria.RenderType.INTEGER);
        }
        sb.getOrCreatePlayerScore(timerName, obj).setScore((int) currentSeconds);
    }

    public static void clearScoreboardTimer(MinecraftServer server) {
        Scoreboard sb = server.getScoreboard();
        Objective obj = sb.getObjective(OBJECTIVE_NAME);
        if (obj != null) sb.removeObjective(obj);
    }
}