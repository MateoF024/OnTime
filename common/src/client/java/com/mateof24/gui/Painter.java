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

    /**
     * Text with a drop shadow, which is what the panel's own text always uses.
     *
     * <p>The panel floats over the world, and grey text without a shadow
     * disappears entirely against a bright sky — which is exactly what happened
     * to the first version of this screen. Hierarchy comes from position and
     * spacing; colour is reserved for saying what state something is in.</p>
     */
    void text(Component text, int x, int y, int argb);

    /**
     * Text with no shadow, on its own opaque panel.
     *
     * <p>The single exception to the rule above, and only for the completion
     * list: that control is a copy of the one chat draws for commands, down to
     * the panel colour and the line height, and vanilla draws its rows flat. A
     * shadow there would be the one detail that gave it away as a lookalike.
     * The dark panel underneath is what makes it legible, so the reason the
     * rule exists does not apply.</p>
     */
    void flatText(String text, int x, int y, int argb);

    /** Filled rectangle, x/y being the top-left corner. */
    void rect(int x, int y, int width, int height, int argb);

    /** Width of a component in pixels, for laying out around it. */
    int textWidth(Component text);

    /** Height of one line of text. */
    int lineHeight();

    /**
     * Scales everything drawn until {@link #popScale()}, about a fixed point.
     *
     * <p>The only call here that needs the pose stack, and the one place the
     * eras genuinely differ: 1.21.1 has {@code PoseStack.pushPose} and takes
     * three axes, the later ones have {@code pushMatrix} and take two. The
     * position picker needs it because showing what a scale of three looks
     * like means drawing at three, not drawing a bigger box around the same
     * text.</p>
     *
     * @param originX the point that stays put, in unscaled screen pixels
     */
    default void pushScale(float scale, int originX, int originY) {}

    /** Undoes the last {@link #pushScale}. */
    default void popScale() {}

    /** One-pixel outline. Drawn as four rects because that is portable everywhere. */
    default void outline(int x, int y, int width, int height, int argb) {
        rect(x, y, width, 1, argb);
        rect(x, y + height - 1, width, 1, argb);
        rect(x, y + 1, 1, height - 2, argb);
        rect(x + width - 1, y + 1, 1, height - 2, argb);
    }
}
