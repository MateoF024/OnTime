package com.mateof24.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 26.x variant: Minecraft 26.1 removed {@code GuiGraphics}; HUD drawing goes
 * through {@link GuiGraphicsExtractor}. Custom renderers targeting 26.x must
 * be compiled against this signature (see the 4.0.0 changelog).
 */
public interface ITimerRenderer {
    void render(GuiGraphicsExtractor graphics, float partialTick,
                String formattedTime, float percentage, int x, int y, float scale);
}
