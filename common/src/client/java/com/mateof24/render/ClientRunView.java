package com.mateof24.render;

import com.mateof24.config.TimerPositionPreset;
import com.mateof24.network.RunView;

/**
 * One execution as the client tracks it, with its own prediction anchor.
 *
 * <p>The nanoTime-anchored prediction and the snap/clamp rules are carried over
 * unchanged from the single-timer client state. They are what keep the counter
 * reading like a wall clock when sync packets land a tick early or late, and
 * they now belong per view: two runs drift independently, so one shared anchor
 * would make them fight.</p>
 */
public final class ClientRunView {

    private static final long NANOS_PER_TICK = 50_000_000L;
    /**
     * Beyond this gap between predicted and received ticks we snap; otherwise
     * we clamp the visible correction to ±1 tick so the displayed second stays
     * monotonic under network jitter.
     */
    private static final long SNAP_THRESHOLD_TICKS = 20;

    private final java.util.UUID runId;
    private String timerName = "";
    private long targetTicks;
    private boolean countUp;
    private boolean running;
    private boolean silent;

    private String preset = "BOSSBAR";
    private int x = -1;
    private int y = 4;
    private float scale = 1.0f;

    // This run's own look and sound, not a shared default. Seeded with the
    // vanilla-ish values only so a view that has never been synced draws
    // something rather than black.
    private int colorHigh = 0xFFFFFF;
    private int colorMid = 0xFFFF00;
    private int colorLow = 0xFF0000;
    private int thresholdMid = 30;
    private int thresholdLow = 10;
    private String soundId = "minecraft:block.note_block.hat";
    private float soundVolume = 1.0f;
    private float soundPitch = 2.0f;

    // Raw title specs plus a Component cache keyed by the raw string. Slot
    // order matches TitleLayout: 0=above 1=below 2=left 3=right.
    private final String[] titleRaw = {"", "", "", ""};
    private final net.minecraft.network.chat.Component[] titleParsed =
            new net.minecraft.network.chat.Component[4];
    private final String[] titleParsedFrom = {null, null, null, null};

    private long currentTicksAtSync = 0;
    private long realTimeAtSyncNanos = 0;
    private boolean hasSync = false;

    private long pauseStartedAtNanos = 0;
    private long pausedTicksSnapshot = 0;
    private boolean wasPaused = false;

    private long lastSecond = -1;

    ClientRunView(java.util.UUID runId) {
        this.runId = runId;
    }

    public java.util.UUID runId() { return runId; }
    public String timerName() { return timerName; }
    public boolean isRunning() { return running; }
    public boolean isSilent() { return silent; }

    /**
     * This execution as the API sees it, for a custom renderer.
     *
     * <p>Audience, mode, phase and owner are null: the server sends a client
     * the executions it may see, not who else sees them, and inventing a
     * plausible value would be worse than saying "not known here".</p>
     */
    public com.mateof24.api.TimerRunInfo toApiInfo() {
        return new com.mateof24.api.TimerRunInfo(
                runId, timerName, getInterpolatedTicks(), targetTicks, countUp, running,
                null, null, null, null, 0);
    }

    /**
     * Ticks left before this execution ends, counting either way round.
     *
     * <p>It only ever decreases while a run ticks, which is what lets the
     * audible clock be picked fresh every tick without it flapping between
     * two of them.</p>
     */
    long remainingTicks() {
        long current = getInterpolatedTicks();
        return countUp ? Math.max(0L, targetTicks - current) : Math.max(0L, current);
    }
    public float scale() { return scale; }
    public int displayX() { return x; }
    public int displayY() { return y; }
    public TimerPositionPreset positionPreset() { return TimerPositionPreset.fromString(preset); }

    /** Applies a snapshot, keeping the prediction anchor coherent. */
    void apply(RunView view) {
        long now = System.nanoTime();
        boolean firstSync = !hasSync;
        boolean nameChanged = !timerName.equals(view.timerName());
        boolean wasRunning = running;

        timerName = view.timerName();
        targetTicks = view.targetTicks();
        countUp = view.countUp();
        silent = view.silent();
        preset = view.preset();
        x = view.x();
        y = view.y();
        scale = view.scale();
        colorHigh = view.colorHigh();
        colorMid = view.colorMid();
        colorLow = view.colorLow();
        thresholdMid = view.thresholdMid();
        thresholdLow = view.thresholdLow();
        soundId = view.soundId();
        soundVolume = view.soundVolume();
        soundPitch = view.soundPitch();
        setTitles(view.titleAbove(), view.titleBelow(), view.titleLeft(), view.titleRight());

        long current = view.currentTicks();
        boolean run = view.running();
        boolean shouldSnap = firstSync || nameChanged || !run || !wasRunning;

        if (shouldSnap) {
            currentTicksAtSync = current;
            realTimeAtSyncNanos = now;
            if (firstSync || nameChanged || !run) lastSecond = current / 20L;
        } else {
            long predicted = computeTicksAt(now);
            long diff = current - predicted;
            long absDiff = Math.abs(diff);

            if (absDiff > SNAP_THRESHOLD_TICKS) {
                currentTicksAtSync = current;
                realTimeAtSyncNanos = now;
                lastSecond = current / 20L;
            } else if (absDiff == 0) {
                currentTicksAtSync = current;
                realTimeAtSyncNanos = now;
            } else {
                long step = Long.signum(diff);
                currentTicksAtSync = predicted + step;
                realTimeAtSyncNanos = now;
            }
        }

        running = run;
        wasPaused = false;
        hasSync = true;
    }

    private void setTitles(String above, String below, String left, String right) {
        titleRaw[TitleLayout.ABOVE] = above != null ? above : "";
        titleRaw[TitleLayout.BELOW] = below != null ? below : "";
        titleRaw[TitleLayout.LEFT] = left != null ? left : "";
        titleRaw[TitleLayout.RIGHT] = right != null ? right : "";
    }

    public boolean hasTitles() {
        return !titleRaw[0].isEmpty() || !titleRaw[1].isEmpty()
                || !titleRaw[2].isEmpty() || !titleRaw[3].isEmpty();
    }

    /**
     * Parsed title of the given slot (null when unset). Cached by the raw
     * string; an invalid spec — which the server validates against — falls back
     * to the literal text.
     */
    public net.minecraft.network.chat.Component titleComponent(int slot) {
        String raw = titleRaw[slot];
        if (raw.isEmpty()) return null;
        if (!raw.equals(titleParsedFrom[slot])) {
            net.minecraft.network.chat.Component parsed = null;
            try {
                parsed = com.mateof24.compat.VanillaCompat.parseTitle(raw);
            } catch (Throwable ignored) {}
            titleParsed[slot] = parsed != null ? parsed
                    : net.minecraft.network.chat.Component.literal(raw);
            titleParsedFrom[slot] = raw;
        }
        return titleParsed[slot];
    }

    /** Freezes prediction while the game is paused. */
    void onClientPaused() {
        if (!wasPaused) {
            pausedTicksSnapshot = computeTicksAt(System.nanoTime());
            pauseStartedAtNanos = System.nanoTime();
            wasPaused = true;
        }
    }

    /** Shifts the anchor forward so prediction continues from where it stopped. */
    void onClientResumed() {
        if (wasPaused) {
            realTimeAtSyncNanos += System.nanoTime() - pauseStartedAtNanos;
            wasPaused = false;
        }
    }

    /** @return true when the displayed second changed and a tick sound is due */
    boolean advanceSecond() {
        if (!running) return false;
        long currentSecond = computeTicksAt(System.nanoTime()) / 20L;
        boolean crossed = currentSecond != lastSecond && lastSecond != -1;
        lastSecond = currentSecond;
        return crossed && !silent;
    }

    private long computeTicksAt(long nowNanos) {
        if (!hasSync) return 0;
        if (wasPaused) return pausedTicksSnapshot;
        if (!running) return currentTicksAtSync;
        long elapsedTicks = (nowNanos - realTimeAtSyncNanos) / NANOS_PER_TICK;
        if (elapsedTicks < 0) elapsedTicks = 0;
        if (countUp) return Math.min(currentTicksAtSync + elapsedTicks, targetTicks);
        return Math.max(currentTicksAtSync - elapsedTicks, 0);
    }

    public long getInterpolatedTicks() {
        return computeTicksAt(System.nanoTime());
    }

    public String getFormattedTime() {
        return ClientTimerState.formatTicks(getInterpolatedTicks());
    }

    public String soundId() { return soundId; }
    public float soundVolume() { return soundVolume; }
    public float soundPitch() { return soundPitch; }

    /**
     * The colour to draw this run in, right now.
     *
     * <p>One method, used by the HUD and by the admin panel alike, so the two
     * cannot disagree about where a threshold falls.</p>
     */
    public int currentColor() {
        float percentage = getPercentage();
        if (percentage >= thresholdMid) return colorHigh;
        if (percentage >= thresholdLow) return colorMid;
        return colorLow;
    }

    public float getPercentage() {
        long ticks = getInterpolatedTicks();
        if (targetTicks == 0) return 100f;
        float percentage = (ticks * 100f) / targetTicks;
        return countUp ? 100f - percentage : percentage;
    }
}
