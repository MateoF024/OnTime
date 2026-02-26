package com.mateof24.render;

import com.mateof24.config.TimerPositionPreset;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class TimerRenderer {

    public static void render(GuiGraphics graphics, float tickDelta) {
        if (!ClientTimerState.shouldDisplay()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.options.renderDebug) return;

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

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(x, y, 0);
        poseStack.scale(scale, scale, 1.0f);
        graphics.drawString(mc.font, timeText, 1, 1, 0x000000, false);
        graphics.drawString(mc.font, timeText, 0, 0, textColor, false);
        poseStack.popPose();
    }
}