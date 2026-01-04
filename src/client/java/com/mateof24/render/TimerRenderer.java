package com.mateof24.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class TimerRenderer {
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int Y_OFFSET = 12;

    // Render timer on HUD
    public static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker tickDelta) {
        if (!ClientTimerState.shouldDisplay()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }

        String timeText = ClientTimerState.getFormattedTime();

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int textWidth = mc.font.width(timeText);
        int x = (screenWidth - textWidth) / 2;

        // Draw shadow for better visibility
        graphics.drawString(mc.font, timeText, x + 1, Y_OFFSET + 1, 0x000000, false);
        // Draw main text
        graphics.drawString(mc.font, timeText, x, Y_OFFSET, TEXT_COLOR, false);
    }
}