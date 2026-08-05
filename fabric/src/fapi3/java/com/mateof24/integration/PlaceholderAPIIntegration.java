package com.mateof24.integration;

import com.mateof24.api.OnTimeAPI;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Wires the {@code ontime:*} placeholders. All the logic is in
 * {@link PlaceholderResolvers}; this file only knows how this Fabric API
 * generation spells an id and registers a placeholder.
 */
public class PlaceholderAPIIntegration {

    public static void register(OnTimeAPI api) {
        for (var entry : PlaceholderResolvers.all().entrySet()) {
            PlaceholderResolvers.Resolver resolver = entry.getValue();
            Placeholders.registerServer(Identifier.fromNamespaceAndPath("ontime", entry.getKey()),
                    (ctx, arg) -> {
                        var player = ctx.player();
                        Component value = resolver.resolve(player == null ? null : player.getUUID(), arg);
                        return value == null
                                ? PlaceholderResult.invalid("Unknown or missing timer: " + arg)
                                : PlaceholderResult.value(value);
                    });
        }
    }
}
