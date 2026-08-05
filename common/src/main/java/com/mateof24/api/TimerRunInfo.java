package com.mateof24.api;

import java.util.UUID;

/**
 * One execution of a timer, as an immutable snapshot.
 *
 * <p>This is the type most of the API deals in, because an execution is what
 * actually happens: a timer can have none in flight, one, or one per player,
 * and each carries its own clock. Everything that belongs to the template
 * instead — the finish command, the titles, the repeat settings — lives on
 * {@link TimerDefinition}, and that split is the whole point.</p>
 *
 * <p><b>On the client.</b> A custom renderer receives one of these per
 * execution it draws, but a client is only told about the executions it can
 * see, not who else sees them or how they were started. So client-side
 * {@code mode}, {@code phase}, {@code owner} and {@code audience} are
 * <b>null</b>, and identity, clock and running state are the real thing.
 * Server-side every field is populated.</p>
 *
 * @param runId      identifies this execution for as long as it lives; the
 *                   handle every {@code *Run} operation takes
 * @param timerName  the definition it is an execution of
 * @param owner      the player it belongs to, or null unless {@code mode} is
 *                   {@link RunMode#EACH}
 */
public record TimerRunInfo(
        UUID runId,
        String timerName,
        long currentTicks,
        long targetTicks,
        boolean countUp,
        boolean running,
        RunMode mode,
        RunPhase phase,
        UUID owner,
        Audience audience,
        int repeatsDone
) {

    public long currentSeconds() { return currentTicks / 20L; }

    public long targetSeconds() { return targetTicks / 20L; }

    /** Ticks left before it ends, counting either way round. */
    public long remainingTicks() {
        return countUp ? Math.max(0L, targetTicks - currentTicks) : Math.max(0L, currentTicks);
    }

    /** {@code MM:SS}, or {@code HH:MM:SS} past an hour — what the HUD shows. */
    public String formattedTime() {
        long total = currentSeconds();
        long h = total / 3600, m = (total % 3600) / 60, s = total % 60;
        return h > 0 ? String.format("%02d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }

    /** How much is left, as a percentage — 100 at the start, 0 at the end, both ways round. */
    public float percentage() {
        if (targetTicks == 0) return 100f;
        float pct = (currentTicks * 100f) / targetTicks;
        return countUp ? 100f - pct : pct;
    }

    /** True while waiting out a repeat or sequence cooldown, when no clock advances. */
    public boolean inCooldown() { return phase != null && phase != RunPhase.ACTIVE; }

    /** Short form for logs and messages; the {@code {run}} placeholder. */
    public String shortId() { return runId.toString().substring(0, 8); }

    /**
     * Whether this execution is on that player's screen. Always true when the
     * audience is unknown — that is the client case, and a client only ever
     * holds executions it can already see.
     */
    public boolean isVisibleTo(UUID player) {
        return audience == null || audience.includes(player);
    }
}
