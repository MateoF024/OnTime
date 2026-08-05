package com.mateof24.api;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Who sees a {@link TimerRun}.
 *
 * <p>Part of the public API vocabulary, and a type rather than a player list,
 * because "global" and "@a" are not the same thing. A global run belongs to the server: whoever joins later sees it,
 * which is how the single timer of 4.0.0 behaved and what the JOIN handler
 * relies on. A player audience is a fixed set resolved when the run starts, so
 * a later joiner is not part of it.</p>
 */
public final class Audience {

    public enum Scope { GLOBAL, PLAYERS }

    private static final Audience GLOBAL = new Audience(Scope.GLOBAL, Set.of());

    private final Scope scope;
    private final Set<UUID> players;

    private Audience(Scope scope, Set<UUID> players) {
        this.scope = scope;
        this.players = players;
    }

    /** Everyone on the server, including players who connect later. */
    public static Audience global() {
        return GLOBAL;
    }

    /** A fixed set of players. An empty set still means "these zero players", not global. */
    public static Audience ofPlayers(Collection<UUID> players) {
        return new Audience(Scope.PLAYERS, Collections.unmodifiableSet(new LinkedHashSet<>(players)));
    }

    public static Audience ofPlayer(UUID player) {
        return ofPlayers(Set.of(player));
    }

    public Scope scope() { return scope; }

    public boolean isGlobal() { return scope == Scope.GLOBAL; }

    /** Empty for a global audience — use {@link #includes} rather than reading this. */
    public Set<UUID> players() { return players; }

    public boolean includes(UUID player) {
        return scope == Scope.GLOBAL || players.contains(player);
    }

    /** Number of players explicitly listed; 0 for a global audience. */
    public int size() { return players.size(); }

    /**
     * Whether both audiences would reach at least one player in common.
     *
     * <p>A global audience overlaps everything, since it covers the whole
     * server including anyone the other one lists. This is what decides
     * whether two runs of the same timer can coexist.</p>
     */
    public boolean overlaps(Audience other) {
        if (scope == Scope.GLOBAL || other.scope == Scope.GLOBAL) return true;
        for (UUID player : players) {
            if (other.players.contains(player)) return true;
        }
        return false;
    }

    public Audience withAdded(Collection<UUID> extra) {
        if (scope == Scope.GLOBAL) return this;
        Set<UUID> merged = new LinkedHashSet<>(players);
        merged.addAll(extra);
        return ofPlayers(merged);
    }

    public Audience withRemoved(Collection<UUID> gone) {
        if (scope == Scope.GLOBAL) return this;
        Set<UUID> merged = new LinkedHashSet<>(players);
        merged.removeAll(gone);
        return ofPlayers(merged);
    }

    @Override
    public String toString() {
        return scope == Scope.GLOBAL ? "global" : "players[" + players.size() + "]";
    }
}
