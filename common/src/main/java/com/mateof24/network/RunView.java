package com.mateof24.network;

/**
 * One execution as a client needs to see it.
 *
 * <p>Deliberately made of JDK types only: this is the wire shape, and keeping
 * it free of Minecraft types lets the whole "what does this player see"
 * decision live in common, with the per-loader code reduced to a codec.</p>
 *
 * <p>{@code preset}, {@code x}, {@code y} and {@code scale} are already
 * resolved here — the timer's own override if it has one, the global default
 * otherwise — so the client never has to know which of the two it got.</p>
 */
public record RunView(
        java.util.UUID runId,
        String timerName,
        long currentTicks,
        long targetTicks,
        boolean countUp,
        boolean running,
        boolean silent,
        String titleAbove,
        String titleBelow,
        String titleLeft,
        String titleRight,
        String preset,
        int x,
        int y,
        float scale
) {
}
