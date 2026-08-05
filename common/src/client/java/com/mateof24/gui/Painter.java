package com.mateof24.gui;

import net.minecraft.network.chat.Component;

/**
 * The four drawing calls the panel needs.
 *
 * <p>This exists because of one rename: Minecraft 26.1 replaced
 * {@code GuiGraphics} with {@code GuiGraphicsExtractor} and {@code drawString}
 * with {@code text}. Everything else the panel draws with is identical across
 * every version the mod ships for. Rather than copy the whole screen three
 * times to accommodate that, the screen is written once against this and each
 * era supplies a two-dozen-line implementation.</p>
 *
 * <p>{@code Component} and {@code Font} are Minecraft types but stable ones, so
 * they cross this boundary freely.</p>
 */
public interface Painter {

    /** Text with a drop shadow, which is what vanilla uses for anything on a dark panel. */
    void text(Component text, int x, int y, int argb);

    /** Text without a shadow, for dimmer secondary rows. */
    void flatText(Component text, int x, int y, int argb);

    /** Text centred horizontally on {@code centerX}. */
    void centeredText(Component text, int centerX, int y, int argb);

    /** Filled rectangle, x/y being the top-left corner. */
    void rect(int x, int y, int width, int height, int argb);

    /** Width of a component in pixels, for laying out around it. */
    int textWidth(Component text);

    /** Height of one line of text. */
    int lineHeight();

    /** One-pixel outline. Drawn as four rects because that is portable everywhere. */
    default void outline(int x, int y, int width, int height, int argb) {
        rect(x, y, width, 1, argb);
        rect(x, y + height - 1, width, 1, argb);
        rect(x, y + 1, 1, height - 2, argb);
        rect(x + width - 1, y + 1, 1, height - 2, argb);
    }
}
