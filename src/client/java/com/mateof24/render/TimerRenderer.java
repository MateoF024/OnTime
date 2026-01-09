package com.mateof24.render;

import com.mateof24.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class TimerRenderer {
    private static final int Y_OFFSET = 4;

    public static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker tickDelta) {
        if (!ClientTimerState.shouldDisplay()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }

        String timeText = ClientTimerState.getFormattedTime();
        float percentage = ClientTimerState.getPercentage();
        int textColor = ModConfig.getInstance().getColorForPercentage(percentage);

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int textWidth = mc.font.width(timeText);
        int x = (screenWidth - textWidth) / 2;

        graphics.drawString(mc.font, timeText, x + 1, Y_OFFSET + 1, 0x000000, false);
        graphics.drawString(mc.font, timeText, x, Y_OFFSET, textColor, false);
    }
}