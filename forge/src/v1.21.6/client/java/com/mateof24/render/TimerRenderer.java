package com.mateof24.render;

import com.mateof24.config.TimerPositionPreset;
import com.mateof24.network.ClientTimerState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class TimerRenderer {

    public static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker tickDelta) {
        if (!ClientTimerState.shouldDisplay()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;

        String timeText = ClientTimerState.getFormattedTime();
        int textColor = ClientTimerState.getColorForPercentage(ClientTimerState.getPercentage());

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        TimerPositionPreset preset = ClientTimerState.getPositionPreset();
        float scale = ClientTimerState.getDisplayScale();
        int textWidth = (int) (mc.font.width(timeText) * scale);
        int textHeight = (int) (mc.font.lineHeight * scale);

        int x, y;
        if (preset == TimerPositionPreset.CUSTOM) {
            int cfgX = ClientTimerState.getDisplayX();
            x = cfgX == -1 ? (screenWidth - textWidth) / 2 : cfgX;
            y = ClientTimerState.getDisplayY();
        } else {
            x = preset.calculateX(screenWidth, textWidth, ClientTimerState.getDisplayX());
            y = preset.calculateY(screenHeight, textHeight, ClientTimerState.getDisplayY());
            if (x == -1) x = (screenWidth - textWidth) / 2;
        }

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