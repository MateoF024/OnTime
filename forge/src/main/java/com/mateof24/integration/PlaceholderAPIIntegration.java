package com.mateof24.integration;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Resolves the {@code ontime:*} placeholders for NeoForge.
 *
 * <p>All the logic is in {@link PlaceholderResolvers}, shared with the Fabric
 * side; this only unwraps the command source and flattens the result to text.
 * It used to answer six {@code active_*} names with its own copy of the logic,
 * which is why Fabric quietly had five more than NeoForge did.</p>
 */
public class PlaceholderAPIIntegration {

    private static final String PREFIX = "ontime:";

    /**
     * @param placeholder {@code ontime:<name>} or {@code ontime:<name>:<arg>}
     * @return the resolved text, or null when the name is unknown or the
     *         argument names no timer
     */
    public static String resolve(String placeholder, CommandSourceStack source) {
        if (placeholder == null || !placeholder.startsWith(PREFIX)) return null;

        String body = placeholder.substring(PREFIX.length());
        String name = body;
        String arg = null;
        int colon = body.indexOf(':');
        if (colon >= 0) {
            name = body.substring(0, colon);
            arg = body.substring(colon + 1);
        }

        PlaceholderResolvers.Resolver resolver = PlaceholderResolvers.all().get(name);
        if (resolver == null) return null;

        UUID player = source != null && source.getEntity() instanceof ServerPlayer p ? p.getUUID() : null;
        Component value = resolver.resolve(player, arg);
        return value == null ? null : value.getString();
    }
}
