package com.mateof24.integration;

import com.mateof24.api.OnTimeAPI;
import com.mateof24.api.TimerRunInfo;
import com.mateof24.config.ModConfig;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What every {@code ontime:*} placeholder resolves to.
 *
 * <p>The four integrations that expose these — three Fabric flavours and
 * NeoForge — used to carry a copy of the logic each, differing only in whether
 * the id type is called {@code ResourceLocation} or {@code Identifier}. Adding
 * one placeholder meant writing it four times and hoping the copies agreed.
 * They now only wire ids to the resolvers below.</p>
 */
public final class PlaceholderResolvers {

    private PlaceholderResolvers() {}

    /**
     * @param player the player the placeholder is being resolved for, or null
     *               when there is no player in context
     * @param arg    the part after the colon, or null when there is none
     * @return the value, or null when the placeholder cannot be resolved
     *         (unknown timer, missing argument) — the caller decides how to
     *         report that
     */
    @FunctionalInterface
    public interface Resolver {
        Component resolve(UUID player, String arg);
    }

    private static final Map<String, Resolver> RESOLVERS = new LinkedHashMap<>();

    /** Placeholder name (without the {@code ontime:} namespace) to resolver. */
    public static Map<String, Resolver> all() {
        return java.util.Collections.unmodifiableMap(RESOLVERS);
    }

    private static OnTimeAPI api() { return OnTimeAPI.getInstance(); }

    /** The oldest execution in flight; what {@code active_*} has always meant. */
    private static TimerRunInfo primary() {
        return api().getPrimaryRun().orElse(null);
    }

    /** The asking player's own execution, oldest first when they have several. */
    private static TimerRunInfo mine(UUID player) {
        if (player == null) return null;
        List<TimerRunInfo> runs = api().getRunsFor(player);
        return runs.isEmpty() ? null : runs.get(0);
    }

    private static Component coloured(TimerRunInfo run) {
        // The timer's own colours. Falling back to the defaults would colour a
        // placeholder differently from the counter it is quoting.
        int color = com.mateof24.manager.TimerManager.getInstance().getTimer(run.timerName())
                .map(timer -> timer.display().colorFor(run.percentage()))
                .orElseGet(() -> ModConfig.getInstance().getColorForPercentage(run.percentage()));
        return Component.literal(run.formattedTime()).withStyle(s -> s.withColor(0xFF000000 | color));
    }

    private static void register(String prefix, java.util.function.Function<UUID, TimerRunInfo> source) {
        RESOLVERS.put(prefix + "_name", (p, a) -> {
            TimerRunInfo run = source.apply(p);
            return run == null ? Component.empty() : Component.literal(run.timerName());
        });
        RESOLVERS.put(prefix + "_time", (p, a) -> {
            TimerRunInfo run = source.apply(p);
            return run == null ? Component.empty() : coloured(run);
        });
        RESOLVERS.put(prefix + "_percent", (p, a) -> {
            TimerRunInfo run = source.apply(p);
            return Component.literal(run == null ? "0" : String.format("%.1f", run.percentage()));
        });
        RESOLVERS.put(prefix + "_running", (p, a) -> {
            TimerRunInfo run = source.apply(p);
            return Component.literal(String.valueOf(run != null && run.running()));
        });
        RESOLVERS.put(prefix + "_mode", (p, a) -> {
            TimerRunInfo run = source.apply(p);
            return run == null ? Component.empty()
                    : Component.literal(run.countUp() ? "count-up" : "countdown");
        });
        RESOLVERS.put(prefix + "_seconds", (p, a) -> {
            TimerRunInfo run = source.apply(p);
            return Component.literal(String.valueOf(run == null ? 0 : run.currentSeconds()));
        });
    }

    static {
        // active_* keeps meaning what it did in 4.x: the oldest execution in
        // flight. With several running that is an arbitrary pick, which is why
        // my_* exists — a per-player timer wants the asking player's own.
        register("active", p -> primary());
        register("my", PlaceholderResolvers::mine);

        RESOLVERS.put("count", (p, a) -> Component.literal(String.valueOf(api().getRuns().size())));
        RESOLVERS.put("my_count", (p, a) ->
                Component.literal(String.valueOf(p == null ? 0 : api().getRunsFor(p).size())));

        // timer_* address a definition by name; the argument is required.
        RESOLVERS.put("timer_exists", (p, arg) ->
                arg == null || arg.isEmpty() ? null : Component.literal(String.valueOf(api().hasTimer(arg))));
        RESOLVERS.put("timer_time", (p, arg) -> byName(arg, PlaceholderResolvers::coloured));
        RESOLVERS.put("timer_percent", (p, arg) ->
                byName(arg, run -> Component.literal(String.format("%.1f", run.percentage()))));
        RESOLVERS.put("timer_running", (p, arg) ->
                byName(arg, run -> Component.literal(String.valueOf(run.running()))));
        RESOLVERS.put("timer_seconds", (p, arg) ->
                byName(arg, run -> Component.literal(String.valueOf(run.currentSeconds()))));
    }

    /**
     * Resolves a {@code timer_*} placeholder against that timer's oldest
     * execution. A timer with nothing in flight has no clock to report, so it
     * answers with its starting time — which is what it will show when started.
     */
    private static Component byName(String name, java.util.function.Function<TimerRunInfo, Component> mapper) {
        if (name == null || name.isEmpty()) return null;
        List<TimerRunInfo> runs = api().getRunsOf(name);
        if (!runs.isEmpty()) return mapper.apply(runs.get(0));

        return api().getDefinition(name)
                .map(def -> mapper.apply(new TimerRunInfo(
                        new UUID(0L, 0L), def.name(),
                        def.countUp() ? 0L : def.targetTicks(), def.targetTicks(),
                        def.countUp(), false, null, null, null, null, 0)))
                .orElse(null);
    }
}
