package com.mateof24.gui;

/**
 * Vanilla's tooltip, on the axis that tracks it.
 *
 * <p>The one call the panel makes that changed shape inside a single screen
 * era: 1.21.1 and 1.21.5 draw one immediately from a list of lines, and from
 * 1.21.6 the game takes a component and draws it after everything else. That
 * is the same boundary the pose stack moved on, which is why this lives here
 * beside {@link PoseScale} rather than in the screens.</p>
 */
final class Tooltips {

    private Tooltips() {}

    static void show(net.minecraft.client.gui.GuiGraphics graphics,
                     net.minecraft.client.gui.Font font,
                     net.minecraft.network.chat.Component text, int mouseX, int mouseY) {
        // 1.21.1 and 1.21.5 draw it there and then, from a list of lines.
        graphics.renderTooltip(font, java.util.List.of(text.getVisualOrderText()), mouseX, mouseY);
    }
}
