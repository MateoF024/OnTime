package com.mateof24.api;

/**
 * What an execution is doing between ticks.
 *
 * <p>Only {@link #ACTIVE} runs advance a clock. The two cooldown phases are the
 * gap a repeating or sequenced timer waits out, during which the execution is
 * still registered but nothing is counting.</p>
 */
public enum RunPhase {
    /** Ticking, or paused — either way it is the timer on screen. */
    ACTIVE,
    /** Finished a lap, waiting out the repeat cooldown before the next one. */
    REPEAT_COOLDOWN,
    /** Finished, waiting out the sequence cooldown before the next timer starts. */
    SEQUENCE_COOLDOWN
}
