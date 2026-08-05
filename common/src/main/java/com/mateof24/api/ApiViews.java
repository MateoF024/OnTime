package com.mateof24.api;

import com.mateof24.timer.Timer;
import com.mateof24.timer.TimerRun;

/**
 * The one place internal state is turned into API snapshots.
 *
 * <p>Internal only — a consumer never calls this, it only receives what it
 * produces. It exists so that every surface (the API itself, the event bus, the
 * renderer hook, the placeholders) builds the same snapshot from the same
 * fields, instead of four hand-rolled conversions drifting apart.</p>
 */
public final class ApiViews {

    private ApiViews() {}

    public static TimerRunInfo of(TimerRun run) {
        return new TimerRunInfo(
                run.runId(),
                run.timerName(),
                run.getCurrentTicks(),
                run.getTargetTicks(),
                run.isCountUp(),
                run.isRunning(),
                run.mode(),
                run.phase(),
                run.owner(),
                run.audience(),
                run.getRepeatsDone());
    }

    public static TimerDefinition of(Timer timer) {
        java.util.Map<Long, java.util.List<String>> scheduled = new java.util.LinkedHashMap<>();
        for (Timer.CommandEvent event : timer.getCommandEvents()) {
            scheduled.put(event.getAtSeconds(), java.util.List.copyOf(event.getCommands()));
        }

        java.util.Map<String, String> titles = new java.util.LinkedHashMap<>();
        for (String slot : new String[]{"above", "below", "left", "right"}) {
            String raw = timer.getTitle(slot);
            if (raw != null) titles.put(slot, raw);
        }

        return new TimerDefinition(
                timer.getName(),
                timer.getTargetTicks(),
                timer.isCountUp(),
                timer.isSilent(),
                emptyToNull(timer.getCommand()),
                java.util.List.copyOf(timer.getFinishCommands()),
                java.util.Collections.unmodifiableMap(scheduled),
                java.util.Collections.unmodifiableMap(titles),
                timer.isRepeat(),
                timer.getRepeatCount(),
                timer.getRepeatCooldownTicks(),
                timer.getNextTimer(),
                timer.getSequenceCooldownTicks(),
                timer.hasCondition() ? timer.getConditionObjective() : null,
                timer.getConditionScore(),
                timer.getConditionTarget(),
                timer.getScoreConditionAction(),
                timer.getConditionExpression(),
                timer.getConditionExpressionAction(),
                timer.getTriggerType(),
                timer.getTriggerAction(),
                timer.display().preset(),
                timer.display().x(),
                timer.display().y(),
                timer.display().scale());
    }

    /**
     * The legacy snapshot, kept for the deprecated events and renderer hook.
     *
     * <p>Built from an execution rather than from the definition's mirrored
     * fields, so a listener on a second run at least sees that run's clock —
     * which is as close as the old shape can get to the truth.</p>
     */
    @SuppressWarnings("deprecation")
    public static TimerInfo legacyOf(TimerRun run) {
        Timer timer = run.timer();
        return new TimerInfo(run.timerName(), run.getCurrentTicks(), run.getTargetTicks(),
                run.isCountUp(), run.isRunning(), timer.isSilent(), timer.getCommand(),
                timer.isRepeat(), timer.getRepeatCount(), run.getRepeatsDone());
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}
