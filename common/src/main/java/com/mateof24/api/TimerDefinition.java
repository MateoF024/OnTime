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
 * <p>Unset optional settings are null rather than a sentinel. The display
 * fields are never among them: a timer copies the server defaults when it is
 * created and keeps its own, so {@code position}, {@code customX},
 * {@code customY} and {@code scale} always hold this timer's real values.</p>
 */
public record TimerDefinition(
        String name,
        long targetTicks,
        boolean countUp,
        boolean silent,

        /** Everything this timer runs when it ends, in execution order. */
        List<String> finishCommands,
        /** Seconds on the clock → commands fired when it crosses, in execution order. */
        Map<Long, List<String>> scheduledCommands,
        /** Raw title specs by slot ("above"/"below"/"left"/"right"); unset slots absent. */
        Map<String, String> titles,

        boolean repeat,
        /** -1 for endless. */
        int repeatCount,
        long repeatCooldownTicks,

        /**
         * Ticks between two of this timer's commands.
         *
         * <p>Always a real figure: a timer copies the server default when it
         * is made and owns it from then on, the same way it owns its colours.
         * </p>
         */
        int commandDelayTicks,

        /** Timer started when this one ends, or null. */
        String nextTimer,
        long sequenceCooldownTicks,

        /**
         * Every reason this timer starts or ends other than its own clock.
         *
         * <p>Replaces the three single-valued systems this record used to
         * expose separately — a game event, a scoreboard comparison and an
         * expression, each with its own action field. They are one kind of
         * thing and a timer may now hold any number of them.</p>
         */
        List<com.mateof24.trigger.TriggerRule> triggers,

        /**
         * Where this timer draws and how big. Always set: a timer copies the
         * server defaults when it is created and owns them from then on, so
         * there is no "inherited" case left to report.
         */
        String position,
        Integer customX,
        Integer customY,
        Float scale
) {

    public long targetSeconds() { return targetTicks / 20L; }

    public boolean hasScheduledCommands() { return !scheduledCommands.isEmpty(); }

    public boolean hasTitles() { return !titles.isEmpty(); }
}
