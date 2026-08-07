package com.mateof24.trigger;

import com.mateof24.timer.Timer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a {@link Who} into the players it names, right now.
 *
 * <p>Resolved every time rather than once: a team gains and loses members, and
 * "everybody on the server" is a different set a minute later. A trigger that
 * remembered its players would be asking about people who had logged off.</p>
 */
public final class WhoResolver {

    private WhoResolver() {}

    /** The players this subject covers at this moment; never null. */
    public static List<ServerPlayer> resolve(MinecraftServer server, Timer timer, Who who) {
        List<ServerPlayer> online = server == null
                ? List.of() : server.getPlayerList().getPlayers();
        return switch (who.scope()) {
            case EVERYONE -> new ArrayList<>(online);
            case PLAYERS -> byName(online, who.value());
            case TEAM -> byTeam(online, who.value());
            case SELECTOR -> bySelector(server, who.value(), online);
            case AUDIENCE -> audienceOf(timer, online);
        };
    }

    private static List<ServerPlayer> byName(List<ServerPlayer> online, String csv) {
        List<ServerPlayer> out = new ArrayList<>();
        for (String raw : csv.split(",")) {
            String wanted = raw.trim();
            if (wanted.isEmpty()) continue;
            for (ServerPlayer player : online) {
                if (player.getScoreboardName().equalsIgnoreCase(wanted)) out.add(player);
            }
        }
        return out;
    }

    private static List<ServerPlayer> byTeam(List<ServerPlayer> online, String team) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : online) {
            var current = player.getTeam();
            if (current != null && current.getName().equalsIgnoreCase(team.trim())) out.add(player);
        }
        return out;
    }

    /**
     * A vanilla selector, parsed with vanilla's own parser.
     *
     * <p>Not reimplemented: {@code @a[team=red,tag=x]} has rules, and the only
     * correct copy of them is the one the game ships. A selector that does not
     * parse resolves to nobody rather than to everybody — silently widening a
     * trigger is the worse failure.</p>
     */
    private static List<ServerPlayer> bySelector(MinecraftServer server, String selector,
                                                 List<ServerPlayer> online) {
        if (server == null) return List.of();
        try {
            var source = server.createCommandSourceStack();
            var parser = new net.minecraft.commands.arguments.selector.EntitySelectorParser(
                    new com.mojang.brigadier.StringReader(selector.trim()), true);
            return new ArrayList<>(parser.parse().findPlayers(source));
        } catch (Exception e) {
            com.mateof24.OnTimeConstants.LOGGER.warn(
                    "Trigger selector '{}' could not be read", selector, e);
            return List.of();
        }
    }

    /**
     * Whoever the timer is currently running for.
     *
     * <p>With no execution in flight there is no audience, and a trigger that
     * starts a timer is exactly the case where there is none — so it falls back
     * to everybody, which is what "start this when somebody dies" has always
     * meant.</p>
     */
    private static List<ServerPlayer> audienceOf(Timer timer, List<ServerPlayer> online) {
        if (timer == null) return new ArrayList<>(online);
        List<ServerPlayer> out = new ArrayList<>();
        for (com.mateof24.timer.TimerRun run
                : com.mateof24.manager.TimerManager.getInstance().findRuns(timer.getName(), null)) {
            for (ServerPlayer player : online) {
                if (run.audience().includes(player.getUUID()) && !out.contains(player)) out.add(player);
            }
        }
        return out.isEmpty() ? new ArrayList<>(online) : out;
    }

    /** How many of them the quantifier needs. */
    public static int required(Who who, int subjectSize) {
        return switch (who.quantifier()) {
            case ANY -> 1;
            case ALL -> Math.max(1, subjectSize);
            case AT_LEAST -> Math.max(1, who.count());
        };
    }

    /** Whether a name is one of the players this subject covers. */
    public static boolean covers(MinecraftServer server, Timer timer, Who who, java.util.UUID player) {
        if (player == null) return who.scope() == Who.Scope.EVERYONE;
        for (ServerPlayer candidate : resolve(server, timer, who)) {
            if (candidate.getUUID().equals(player)) return true;
        }
        return false;
    }

    static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
