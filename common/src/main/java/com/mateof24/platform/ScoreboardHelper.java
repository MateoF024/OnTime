package com.mateof24.platform;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.network.chat.Component;

/**
 * Scoreboard access for the {@code ontime_active} objective.
 *
 * <p>This is plain vanilla API — it never belonged in the platform layer. It
 * used to exist as six identical Fabric copies plus a fully-qualified inline
 * copy inside NeoForgePlatformHelper; both loaders delegate here now.</p>
 *
 * <p>The four public signatures are the ones 'main' exposes, so everything
 * calling them is the same file on both branches. The bodies are not: the
 * scoreboard was reworked after 1.20.1 and every line of this class touches
 * the part that changed. Verified against the mapped jar rather than
 * remembered:</p>
 *
 * <ul>
 *   <li>A holder is a {@code String}. {@code ScoreHolder} does not exist, so
 *       {@code ScoreHolder.forNameOnly(name)} is just the name.</li>
 *   <li>A score is a mutable {@code Score} with {@code getScore()} and
 *       {@code setScore(int)}, not a {@code ReadOnlyScoreInfo} record with
 *       {@code value()}.</li>
 *   <li>There is no {@code getPlayerScoreInfo}: reading means asking
 *       {@code hasPlayerScore} first, because {@code getOrCreatePlayerScore}
 *       does what its name says and would leave a zero behind on every check
 *       of a holder that has no score.</li>
 *   <li>{@code addObjective} takes four arguments. The display-auto-update
 *       flag and the number format came later.</li>
 * </ul>
 */
public final class ScoreboardHelper {

    private ScoreboardHelper() {}

    private static final String OBJECTIVE_NAME = "ontime_active";

    public static boolean checkScoreboardCondition(MinecraftServer server, String objectiveName, int score, String target) {
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(objectiveName);
        if (objective == null) return false;

        if ("*".equals(target)) {
            for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (scoreAt(scoreboard, player.getScoreboardName(), objective) >= score) return true;
            }
            return false;
        }

        return scoreboard.hasPlayerScore(target, objective)
                && scoreAt(scoreboard, target, objective) >= score;
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

    public static long getScoreboardValue(MinecraftServer server, String objectiveName, String holderName) {
        Scoreboard sb = server.getScoreboard();
        Objective obj = sb.getObjective(objectiveName);
        if (obj == null || !sb.hasPlayerScore(holderName, obj)) return 0;
        return scoreAt(sb, holderName, obj);
    }

    /** Reads a score that is known to exist. */
    private static int scoreAt(Scoreboard scoreboard, String holder, Objective objective) {
        if (!scoreboard.hasPlayerScore(holder, objective)) return 0;
        Score entry = scoreboard.getOrCreatePlayerScore(holder, objective);
        return entry == null ? 0 : entry.getScore();
    }
}
