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
 * <p>v1 — MC 1.21.1-1.21.4: {@code ClickEvent}/{@code HoverEvent} take an
 * {@code Action} enum plus a payload.</p>
 */
final class HelpStyle {

    private HelpStyle() {}

    /** Style that puts {@code command} in the chat box when clicked. */
    static Style suggest(Style style, String command, Component hover) {
        return style
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
    }

    /** Style that runs {@code command} when clicked. */
    static Style run(Style style, String command, Component hover) {
        return style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
    }
}
