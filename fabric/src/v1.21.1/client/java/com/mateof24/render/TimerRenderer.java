package com.mateof24.render;

import com.mateof24.config.ClientConfig;
import com.mateof24.config.ModConfig;
import com.mateof24.config.TimerPositionPreset;
import com.mojang.blaze3d.vertex.PoseStack;
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
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        ClientConfig config = ClientConfig.getInstance();
        TimerPositionPreset preset = config.getPositionPreset();
        float scale = config.getTimerScale();

        int textWidth = (int) (mc.font.width(timeText) * scale);
        int textHeight = (int) (mc.font.lineHeight * scale);

        // Calcular posición según el preset
        int x, y;

        if (preset == TimerPositionPreset.CUSTOM) {
            // Usar coordenadas guardadas
            int configX = config.getTimerX();
            int configY = config.getTimerY();

            if (configX == -1) {
                x = (screenWidth - textWidth) / 2;
            } else {
                x = configX;
            }
            y = configY;
        } else {
            // Calcular según preset
            int configX = config.getTimerX();
            int configY = config.getTimerY();

            x = preset.calculateX(screenWidth, textWidth, configX);
            y = preset.calculateY(screenHeight, textHeight, configY);

            // Si es centrado horizontalmente
            if (x == -1) {
                x = (screenWidth - textWidth) / 2;
            }
        }

        int shadowColor = 0xFF000000;
        int mainColor = 0xFF000000 | textColor;

        if (scale != 1.0f) {
            var pose = graphics.pose();

            PoseStack poseStack = graphics.pose();
            poseStack.pushPose();
            poseStack.translate(x, y, 0);
            poseStack.scale(scale, scale, 1.0f);

            graphics.drawString(mc.font, timeText, 1, 1, shadowColor, false);
            graphics.drawString(mc.font, timeText, 0, 0, mainColor, false);

            poseStack.popPose();
        } else {
            graphics.drawString(mc.font, timeText, x + 1, y + 1, shadowColor, false);
            graphics.drawString(mc.font, timeText, x, y, mainColor, false);
        }
    }
}