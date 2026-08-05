package com.mateof24.render;

import com.mateof24.config.TimerPositionPreset;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class TimerRenderer {

    public static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker tickDelta) {
        if (!ClientTimerState.shouldDisplay()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;

        for (ClientRunView view : ClientTimerState.visibleViews()) {
        long ticks = view.getInterpolatedTicks();
        String timeText = ClientTimerState.formatTicks(ticks);
        float percentage = view.getPercentage();
        int textColor = ClientTimerState.getColorForPercentage(percentage);

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        TimerPositionPreset preset = view.positionPreset();
        float scale = view.scale();
        int textWidth = (int) (mc.font.width(timeText) * scale);
        int textHeight = (int) (mc.font.lineHeight * scale);

        int x, y;
        if (preset == TimerPositionPreset.CUSTOM) {
            int cfgX = view.displayX();
            x = cfgX == -1 ? (screenWidth - textWidth) / 2 : cfgX;
            y = view.displayY();
        } else {
            x = preset.calculateX(screenWidth, textWidth, view.displayX());
            y = preset.calculateY(screenHeight, textHeight, view.displayY());
            if (x == -1) x = (screenWidth - textWidth) / 2;
        }

        int[] titleAdjusted = TitleOverlay.renderAndShift(view, graphics, x, y, textWidth, textHeight, scale, screenWidth, screenHeight);
        x = titleAdjusted[0];
        y = titleAdjusted[1];

        if (com.mateof24.render.TimerRendererRegistry.hasCustomRenderer()) {
            com.mateof24.render.TimerRendererRegistry.getCustomRenderer()
                    .render(graphics, 0f, view.toApiInfo(), x, y, scale);
            continue;
        }

        int shadowColor = 0xFF000000;
        int mainColor = 0xFF000000 | textColor;

        if (scale != 1.0f) {
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
}
