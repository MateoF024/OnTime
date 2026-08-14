package com.mateof24.trigger;

import com.mateof24.platform.Services;
import com.mateof24.timer.Timer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The engine's one window onto the world.
 *
 * <p>{@link ConditionEngine} has no Minecraft types in it; everything it needs
 * arrives through here. That is what lets the whole of the semantics be tested
 * without a server, and it is also the only place where each kind of condition
 * turns into a concrete question.</p>
 *
 * <p>The two natures meet here too. A state can simply be asked — does this
 * player have ten kills, is this quest complete — while an event cannot: there
 * is no way to poll somebody for "did you die". Events are written into
 * {@link ConditionState}'s inbox as they happen and read back here as though
 * they were states, so the engine above needs to know about only one of the
 * two.</p>
 */
public final class ServerProbe implements ConditionEngine.Probe {

    private final MinecraftServer server;
    private final Timer timer;
    private final ConditionState state;

    public ServerProbe(MinecraftServer server, Timer timer, ConditionState state) {
        this.server = server;
        this.timer = timer;
        this.state = state;
    }

    @Override
    public Set<UUID> subject(Condition.Watch leaf) {
        Set<UUID> out = new HashSet<>();
        for (ServerPlayer player : WhoResolver.resolve(server, timer, leaf.who())) {
            out.add(player.getUUID());
        }
        return out;
    }

    @Override
    public long now() {
        return System.currentTimeMillis();
    }

    @Override
    public boolean holds(Condition.Watch leaf, UUID player) {
        return switch (leaf.kind()) {
            case SCOREBOARD -> scoreboard(leaf, player);
            case EXPRESSION -> expression(leaf);
            case FTB_QUEST -> ftb(leaf, player, true);
            case FTB_REWARD -> ftb(leaf, player, false);
            case ADVANCEMENT -> advancement(leaf, player);
            case DIMENSION_CHANGE -> inDimension(leaf, player);
            case PLAYER_JOIN -> online(player) != null;
            // Leaving, dying and respawning leave nothing to ask about. They
            // are true for the one evaluation that reads them and no longer.
            default -> state.hasEvent(leaf.id(), player);
        };
    }

    /**
     * Whether the player is in that dimension now.
     *
     * <p>Asked rather than remembered from the change, which is the whole
     * point: "in the Nether" and "in the End" are then two things one player
     * cannot be at once, and an "and" of them says so by itself.</p>
     */
    private boolean inDimension(Condition.Watch leaf, UUID player) {
        ServerPlayer online = online(player);
        if (online == null) return false;
        return com.mateof24.compat.VanillaCompat.dimensionId(
                (net.minecraft.server.level.ServerLevel) online.level()).equals(leaf.value());
    }

    private boolean scoreboard(Condition.Watch leaf, UUID player) {
        ServerPlayer online = online(player);
        if (online == null) return false;
        try {
            return Services.PLATFORM.checkScoreboardCondition(server, leaf.value(),
                    leaf.threshold(), online.getScoreboardName());
        } catch (Exception e) {
            com.mateof24.OnTimeConstants.LOGGER.warn(
                    "Could not read objective '{}' for timer '{}'",
                    leaf.value(), timer.getName(), e);
            return false;
        }
    }

    /**
     * One question asked of the server, not of a player.
     *
     * <p>The same answer for everybody in the subject, which is why an
     * expression takes no subject of its own in the editors: quantifying it
     * would count the same answer several times.</p>
     */
    private boolean expression(Condition.Watch leaf) {
        return com.mateof24.command.ConditionEvaluator
                .evaluate(leaf.value(), server, timer)
                .orElse(false);
    }

    /**
     * Asked rather than remembered, which is what makes a reset work.
     *
     * <p>FTB Quests can be reset by an administrator. Because this is a
     * question and not a memory, a reset drops the answer to false, and
     * completing it again is a rising edge the edge memory counts — where the
     * old poller's "already fired" flag would have stayed set for ever.</p>
     */
    private boolean ftb(Condition.Watch leaf, UUID player, boolean quest) {
        if (!com.mateof24.integration.FTBQuestsIntegration.isReady()) return false;
        ServerPlayer online = online(player);
        if (online == null) return false;
        return quest
                ? com.mateof24.integration.FTBQuestsIntegration.hasPlayerCompletedQuest(online, leaf.value())
                : com.mateof24.integration.FTBQuestsIntegration.hasPlayerClaimedReward(online, leaf.value());
    }

    /**
     * Whether the player holds it now.
     *
     * <p>Asked, not taken from the award event, for the reason the whole edge
     * design exists: an advancement that is revoked and earned again has to
     * count twice, and an event that fired once cannot say that.</p>
     */
    private boolean advancement(Condition.Watch leaf, UUID player) {
        ServerPlayer online = online(player);
        if (online == null) return false;
        try {
            // Matched by text rather than by parsing the id: the id type is one
            // of the things that changed name at 26.1, and comparing strings
            // needs no per-version help at all.
            //
            // The holder is the one thing that could not come across unchanged.
            // 1.20.1 has no AdvancementHolder -- verified against the mapped
            // jar -- and getAllAdvancements() hands out Advancement itself,
            // which answers getId() where the holder answers id(). The same
            // loop, one type and one method name apart.
            for (net.minecraft.advancements.Advancement advancement
                    : server.getAdvancements().getAllAdvancements()) {
                if (!advancement.getId().toString().equals(leaf.value())) continue;
                return online.getAdvancements().getOrStartProgress(advancement).isDone();
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private ServerPlayer online(UUID player) {
        return server == null ? null : server.getPlayerList().getPlayer(player);
    }
}
