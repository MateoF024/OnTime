package com.mateof24.integration;

import com.mateof24.api.OnTimeAPI;
import net.minecraft.commands.CommandSourceStack;

public class PlaceholderAPIIntegration {

    private static final OnTimeAPI api = OnTimeAPI.getInstance();

    public static String resolve(String placeholder, CommandSourceStack source) {
        return switch (placeholder) {
            case "ontime:active_name"    -> api.getActiveTimer().map(t -> t.name()).orElse("");
            case "ontime:active_time"    -> api.getActiveTimer().map(t -> t.getFormattedTime()).orElse("");
            case "ontime:active_percent" -> api.getActiveTimer().map(t -> String.format("%.1f", t.getPercentage())).orElse("0");
            case "ontime:active_seconds" -> api.getActiveTimer().map(t -> String.valueOf(t.getCurrentSeconds())).orElse("0");
            case "ontime:active_running" -> api.getActiveTimer().map(t -> String.valueOf(t.running())).orElse("false");
            case "ontime:active_mode"    -> api.getActiveTimer().map(t -> t.countUp() ? "count-up" : "countdown").orElse("");
            default -> null;
        };
    }
}