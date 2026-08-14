package com.mateof24.command;

import com.mateof24.timer.Timer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Sistema de reemplazo de placeholders en comandos
 */
public class PlaceholderSystem {

    private static final Map<String, Function<Timer, String>> PLACEHOLDERS = new HashMap<>();

    static {
        PLACEHOLDERS.put("{name}", Timer::getName);
        PLACEHOLDERS.put("{time}", Timer::getFormattedTime);
        PLACEHOLDERS.put("{ticks}", timer -> String.valueOf(timer.getCurrentTicks()));
        PLACEHOLDERS.put("{target}", timer -> String.valueOf(timer.getTargetTicks()));
        PLACEHOLDERS.put("{mode}", timer -> timer.isCountUp() ? "count-up" : "countdown");
        PLACEHOLDERS.put("{seconds}", timer -> String.valueOf(timer.getCurrentTicks() / 20L));
    }

    public static void registerPlaceholder(String key, Function<Timer, String> resolver) {
        PLACEHOLDERS.put("{" + key + "}", resolver);
    }

    /**
     * Reemplaza todos los placeholders en un comando
     */
    /**
     * Same as {@link #replacePlaceholders(String, Timer)} plus the keys that
     * only mean something for a specific execution.
     *
     * <p>Without these a per-player timer could not act on its own player:
     * {@code {player}} is what makes {@code /give {player} diamond} do the
     * right thing when the same timer is running for twenty people at once.</p>
     */
    public static String replacePlaceholders(String command, com.mateof24.timer.TimerRun run,
                                             net.minecraft.server.MinecraftServer server) {
        if (command == null || command.isEmpty()) return command;

        String result = command;
        if (result.contains("{run}")) result = result.replace("{run}", run.shortId());
        if (result.contains("{uuid}")) {
            result = result.replace("{uuid}", run.owner() != null ? run.owner().toString() : "");
        }
        if (result.contains("{player}")) {
            String name = "";
            if (run.owner() != null && server != null) {
                var player = server.getPlayerList().getPlayer(run.owner());
                if (player != null) name = player.getName().getString();
            }
            result = result.replace("{player}", name);
        }
        return replaceTimerPlaceholders(result, run.timer(), run);
    }

    /** Timer-scoped keys, reading the live clock from the run when there is one. */
    private static String replaceTimerPlaceholders(String command, Timer timer,
                                                   com.mateof24.timer.TimerRun run) {
        String result = command;
        if (run != null) {
            if (result.contains("{time}")) result = result.replace("{time}", run.getFormattedTime());
            if (result.contains("{ticks}")) result = result.replace("{ticks}", String.valueOf(run.getCurrentTicks()));
            if (result.contains("{seconds}")) result = result.replace("{seconds}", String.valueOf(run.getCurrentTicks() / 20L));
        }
        return replacePlaceholders(result, timer);
    }

    public static String replacePlaceholders(String command, Timer timer) {
        if (command == null || command.isEmpty()) {
            return command;
        }

        String result = command;

        for (Map.Entry<String, Function<Timer, String>> entry : PLACEHOLDERS.entrySet()) {
            String placeholder = entry.getKey();
            if (result.contains(placeholder)) {
                String value = entry.getValue().apply(timer);
                result = result.replace(placeholder, value);
            }
        }

        return result;
    }
}