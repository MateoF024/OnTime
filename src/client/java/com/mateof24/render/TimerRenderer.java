package com.mateof24.render;

import com.mateof24.config.ClientConfig;
import com.mateof24.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class TimerRenderer {

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

        ClientConfig config = ClientConfig.getInstance();
        int configX = config.getTimerX();
        int configY = config.getTimerY();
        float scale = config.getTimerScale();

        int textWidth = (int) (mc.font.width(timeText) * scale);

        int x;
        if (configX == -1) {
            x = (screenWidth - textWidth) / 2;
        } else {
            x = configX;
        }

        int y = configY;

        int shadowColor = 0xFF000000;
        int mainColor = 0xFF000000 | textColor;

        if (scale != 1.0f) {
            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(scale, scale);

            graphics.drawString(mc.font, timeText, 1, 1, shadowColor, false);
            graphics.drawString(mc.font, timeText, 0, 0, mainColor, false);

            pose.popMatrix();
        } else {
            graphics.drawString(mc.font, timeText, x + 1, y + 1, shadowColor, false);
            graphics.drawString(mc.font, timeText, x, y, mainColor, false);
        }
    }
}