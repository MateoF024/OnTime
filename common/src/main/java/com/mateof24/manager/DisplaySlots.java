package com.mateof24.manager;

import com.mateof24.config.ModConfig;
import com.mateof24.config.TimerPositionPreset;
import com.mateof24.api.Audience;
import com.mateof24.timer.Timer;
import com.mateof24.timer.TimerRun;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Which screen slot each execution occupies, and who would end up with two of
 * them in the same one.
 *
 * <p>Every preset except {@code CUSTOM} is an anchor: two timers on
 * {@code TOP_LEFT} are drawn literally on top of each other, and the boss bar
 * and action bar hold one line each. So a slot takes one execution — but
 * <em>per viewer</em>, not per server. Two boss-bar timers, one for the red
 * team and one for the blue, never share a screen, and refusing them would be
 * refusing the whole point of audiences.</p>
 */
public final class DisplaySlots {

    private DisplaySlots() {}

    /**
     * The preset an execution of this timer actually draws in: its own
     * override, or the global default when it has none.
     *
     * <p>The single place that resolves it — the sync payload and the conflict
     * check must never disagree about where a timer is.</p>
     */
    public static String presetOf(Timer timer) {
        TimerPositionPreset preset = TimerPositionPreset.parse(timer.display().preset());
        if (preset == null) preset = ModConfig.getInstance().getPositionPreset();
        return preset.name();
    }

    /** True for the one preset that is a free coordinate rather than an anchor. */
    public static boolean isFreeSlot(String preset) {
        return TimerPositionPreset.CUSTOM.name().equals(preset);
    }

    /**
     * An execution already sitting in this slot for someone the audience
     * reaches.
     *
     * @param excludeTimer runs of this timer are ignored: all of a timer's
     *                     executions move together, and two of them can never
     *                     share a viewer anyway (that is what stops a second
     *                     run from starting)
     * @return the occupying run, or null when the slot is free for everyone
     */
    public static TimerRun occupant(String preset, Audience audience, String excludeTimer) {
        if (isFreeSlot(preset)) return null;
        for (TimerRun run : TimerManager.getInstance().runsView()) {
            if (run.timerName().equals(excludeTimer)) continue;
            if (run.isAwaitingSequence()) continue;
            if (!run.audience().overlaps(audience)) continue;
            if (presetOf(run.timer()).equals(preset)) return run;
        }
        return null;
    }

    /**
     * Presets this audience could still be given. {@code CUSTOM} is always in
     * the list — it is the way out when every anchor is taken.
     */
    public static List<String> freeSlots(Audience audience, String excludeTimer) {
        List<String> free = new ArrayList<>();
        for (TimerPositionPreset preset : TimerPositionPreset.values()) {
            if (occupant(preset.name(), audience, excludeTimer) == null) free.add(preset.name().toLowerCase());
        }
        return free;
    }

    /**
     * The players both audiences reach, for the conflict message.
     *
     * @return null when one of them is global, which means "everyone" and has
     *         no useful list to print
     */
    public static Set<UUID> sharedViewers(Audience a, Audience b) {
        if (a.isGlobal() || b.isGlobal()) return null;
        Set<UUID> shared = new LinkedHashSet<>(a.players());
        shared.retainAll(b.players());
        return shared;
    }
}
