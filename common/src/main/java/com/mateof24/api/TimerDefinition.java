package com.mateof24.api;

import java.util.List;
import java.util.Map;

/**
 * A timer's template, as an immutable snapshot: everything an operator
 * configured, and nothing about any execution of it.
 *
 * <p>Reading a field here never tells you whether the timer is running or what
 * time it shows — that is {@link TimerRunInfo}, one per execution. A definition
 * with no execution in flight is perfectly normal and is what
 * {@code /timer create} leaves behind.</p>
 *
 * <p>Unset optional settings are null rather than a sentinel, and null means
 * "inherit the server default" for {@code position}, {@code customX},
 * {@code customY} and {@code scale}.</p>
 */
public record TimerDefinition(
        String name,
        long targetTicks,
        boolean countUp,
        boolean silent,

        /** The single finish command of 4.0.0; null when unset. Runs before {@link #finishCommands()}. */
        String finishCommand,
        List<String> finishCommands,
        /** Seconds on the clock → commands fired when it crosses, in execution order. */
        Map<Long, List<String>> scheduledCommands,
        /** Raw title specs by slot ("above"/"below"/"left"/"right"); unset slots absent. */
        Map<String, String> titles,

        boolean repeat,
        /** -1 for endless. */
        int repeatCount,
        long repeatCooldownTicks,

        /** Timer started when this one ends, or null. */
        String nextTimer,
        long sequenceCooldownTicks,

        /** Scoreboard condition; null objective means none. */
        String conditionObjective,
        int conditionScore,
        String conditionTarget,
        /** "start" or "finish". */
        String conditionAction,

        /** Expression condition, or null. */
        String conditionExpression,
        /** "start" or "finish". */
        String conditionExpressionAction,

        /** Trigger spec, or null. */
        String triggerType,
        /** "start" or "finish". */
        String triggerAction,

        /** Preset name, or null to inherit the server default. */
        String position,
        Integer customX,
        Integer customY,
        Float scale
) {

    public long targetSeconds() { return targetTicks / 20L; }

    public boolean hasScheduledCommands() { return !scheduledCommands.isEmpty(); }

    public boolean hasTitles() { return !titles.isEmpty(); }

    /** True when this timer overrides any of the server display defaults. */
    public boolean hasDisplayOverride() {
        return position != null || customX != null || customY != null || scale != null;
    }
}
