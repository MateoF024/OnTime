package com.mateof24.render;

import net.minecraft.client.gui.GuiGraphics;

public interface ITimerRenderer {
    void render(GuiGraphics graphics, float partialTick,
                String formattedTime, float percentage, int x, int y, float scale);
}