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

    /**
     * Comando predeterminado que se asigna a timers sin comando específico
     */
    public static final String DEFAULT_COMMAND = "say Timer {name} has finished!";

    /**
     * Reemplaza todos los placeholders en un comando
     */
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