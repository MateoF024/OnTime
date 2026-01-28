package com.mateof24.render;

import com.mateof24.config.ClientConfig;
import com.mateof24.config.ModConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class TimerRenderer {

    public static void render(GuiGraphics graphics, float tickDelta) {
        if (!ClientTimerState.shouldDisplay()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.options.hideGui || mc.options.renderDebug) {
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

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(x, y, 0);
        poseStack.scale(scale, scale, 1.0f);

        graphics.drawString(mc.font, timeText, 1, 1, 0x000000, false);
        graphics.drawString(mc.font, timeText, 0, 0, textColor, false);

        poseStack.popPose();
    }
}