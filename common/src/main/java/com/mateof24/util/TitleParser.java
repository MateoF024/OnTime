package com.mateof24.util;

import net.minecraft.network.chat.Component;

/**
 * Parses a timer-title spec (4.0.0): tellraw-style JSON component when it
 * looks like JSON, literal text otherwise. Returns null when the JSON is
 * invalid — callers decide the fallback (commands reject, renderers show
 * the raw string).
 *
 * <p>1.20.1 branch note: main routes this through the per-family compat
 * layer (VanillaCompat.parseTitle, ComponentSerialization codec); this
 * branch predates the compat split and 1.20.1 still ships the classic
 * Gson-based {@code Component.Serializer}.</p>
 */
public final class TitleParser {

    private TitleParser() {}

    public static Component parseTitle(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String trimmed = raw.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return Component.Serializer.fromJson(trimmed);
            } catch (Exception e) {
                return null;
            }
        }
        return Component.literal(raw);
    }
}
