package com.mateof24.integration;

import com.mateof24.api.OnTimeAPI;
import eu.pb4.placeholders.api.Placeholders;
import eu.pb4.placeholders.api.PlaceholderResult;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class PlaceholderAPIIntegration {

    public static void register(OnTimeAPI api) {
        Placeholders.register(new ResourceLocation("ontime", "active_name"),
                (ctx, arg) -> api.getActiveTimer()
                        .map(t -> PlaceholderResult.value(Component.literal(t.name())))
                        .orElse(PlaceholderResult.value(Component.empty())));

        Placeholders.register(new ResourceLocation("ontime", "active_time"),
                (ctx, arg) -> api.getActiveTimer()
                        .map(t -> {
                            int color = com.mateof24.config.ModConfig.getInstance()
                                    .getColorForPercentage(t.getPercentage());
                            return PlaceholderResult.value(
                                    Component.literal(t.getFormattedTime())
                                            .withStyle(s -> s.withColor(0xFF000000 | color))
                            );
                        })
                        .orElse(PlaceholderResult.value(Component.empty())));

        Placeholders.register(new ResourceLocation("ontime", "active_percent"),
                (ctx, arg) -> api.getActiveTimer()
                        .map(t -> PlaceholderResult.value(Component.literal(String.format("%.1f", t.getPercentage()))))
                        .orElse(PlaceholderResult.value(Component.literal("0"))));

        Placeholders.register(new ResourceLocation("ontime", "active_running"),
                (ctx, arg) -> api.getActiveTimer()
                        .map(t -> PlaceholderResult.value(Component.literal(String.valueOf(t.running()))))
                        .orElse(PlaceholderResult.value(Component.literal("false"))));

        Placeholders.register(new ResourceLocation("ontime", "active_mode"),
                (ctx, arg) -> api.getActiveTimer()
                        .map(t -> PlaceholderResult.value(Component.literal(t.countUp() ? "count-up" : "countdown")))
                        .orElse(PlaceholderResult.value(Component.empty())));

        Placeholders.register(new ResourceLocation("ontime", "active_seconds"),
                (ctx, arg) -> api.getActiveTimer()
                        .map(t -> PlaceholderResult.value(Component.literal(String.valueOf(t.getCurrentSeconds()))))
                        .orElse(PlaceholderResult.value(Component.literal("0"))));

        Placeholders.register(new ResourceLocation("ontime", "timer_time"),
                (ctx, arg) -> {
                    if (arg == null || arg.isEmpty()) return PlaceholderResult.invalid("Missing timer name");
                    return api.getTimer(arg)
                            .map(t -> {
                                int color = com.mateof24.config.ModConfig.getInstance()
                                        .getColorForPercentage(t.getPercentage());
                                return PlaceholderResult.value(
                                        Component.literal(t.getFormattedTime())
                                                .withStyle(s -> s.withColor(0xFF000000 | color))
                                );
                            })
                            .orElse(PlaceholderResult.invalid("Timer not found: " + arg));
                });

        Placeholders.register(new ResourceLocation("ontime", "timer_percent"),
                (ctx, arg) -> {
                    if (arg == null || arg.isEmpty()) return PlaceholderResult.invalid("Missing timer name");
                    return api.getTimer(arg)
                            .map(t -> PlaceholderResult.value(Component.literal(String.format("%.1f", t.getPercentage()))))
                            .orElse(PlaceholderResult.invalid("Timer not found: " + arg));
                });

        Placeholders.register(new ResourceLocation("ontime", "timer_running"),
                (ctx, arg) -> {
                    if (arg == null || arg.isEmpty()) return PlaceholderResult.invalid("Missing timer name");
                    return api.getTimer(arg)
                            .map(t -> PlaceholderResult.value(Component.literal(String.valueOf(t.running()))))
                            .orElse(PlaceholderResult.invalid("Timer not found: " + arg));
                });

        Placeholders.register(new ResourceLocation("ontime", "timer_exists"),
                (ctx, arg) -> {
                    if (arg == null || arg.isEmpty()) return PlaceholderResult.invalid("Missing timer name");
                    return PlaceholderResult.value(Component.literal(String.valueOf(api.hasTimer(arg))));
                });

        Placeholders.register(new ResourceLocation("ontime", "timer_seconds"),
                (ctx, arg) -> {
                    if (arg == null || arg.isEmpty()) return PlaceholderResult.invalid("Missing timer name");
                    return api.getTimer(arg)
                            .map(t -> PlaceholderResult.value(Component.literal(String.valueOf(t.getCurrentSeconds()))))
                            .orElse(PlaceholderResult.invalid("Timer not found: " + arg));
                });
    }
}