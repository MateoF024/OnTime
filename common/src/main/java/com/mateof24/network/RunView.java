package com.mateof24.network;

/**
 * One execution as a client needs to see it.
 *
 * <p>Deliberately made of JDK types only: this is the wire shape, and keeping
 * it free of Minecraft types lets the whole "what does this player see"
 * decision live in common, with the per-loader code reduced to a codec.</p>
 *
 * <p>Everything about how the run looks and sounds travels with it. It used to
 * be two packets — the runs in one, a single global set of colours and a sound
 * in another — which is exactly why editing a colour repainted every counter
 * on the server. Now each timer owns its own, so each run carries its own, and
 * there is nothing global left to accidentally share.</p>
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
        float scale,
        int colorHigh,
        int colorMid,
        int colorLow,
        int thresholdMid,
        int thresholdLow,
        String soundId,
        float soundVolume,
        float soundPitch
) {

    /** The colour this run wears right now, at a given percentage of its span. */
    public int colorFor(float percentage) {
        if (percentage >= thresholdMid) return colorHigh;
        if (percentage >= thresholdLow) return colorMid;
        return colorLow;
    }
}
