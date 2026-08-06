package com.mateof24.command;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

/**
 * The only part of the help output that differs across Minecraft versions:
 * how a clickable, hoverable style is built. {@link HelpSystem} itself is
 * shared — it used to be duplicated in full for these four call sites, and the
 * two copies had already drifted apart in their documented usage strings.
 *
 * <p>v2 — MC 1.21.5 and newer: the click and hover events became records, one
 * type per action.</p>
 */
final class HelpStyle {

    private HelpStyle() {}

    /** Style that puts {@code command} in the chat box when clicked. */
    static Style suggest(Style style, String command, Component hover) {
        return style
                .withClickEvent(new ClickEvent.SuggestCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(hover));
    }

    /** Style that opens {@code url} in the browser when clicked. */
    static Style openUrl(Style style, String url, Component hover) {
        return style
                .withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(url)))
                .withHoverEvent(new HoverEvent.ShowText(hover));
    }

    /** Style that runs {@code command} when clicked. */
    static Style run(Style style, String command, Component hover) {
        return style
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(hover));
    }
}
