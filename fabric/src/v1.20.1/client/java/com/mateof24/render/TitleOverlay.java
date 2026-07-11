package com.mateof24.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Draws the four optional decorative titles around the timer (4.0.0) and
 * nudges the counter when a title would not fit on-screen: e.g. an 'above'
 * title at a top preset takes the counter's default spot and pushes the
 * counter down instead of overlapping it.
 * 1.20.1 flavor: GuiGraphics whose pose() is still a PoseStack.
 */
public final class TitleOverlay {

    private TitleOverlay() {}

    /**
     * Measures the titles, shifts the timer rect so everything fits, draws
     * the titles around the shifted rect and returns {adjustedX, adjustedY}
     * for the counter itself.
     */
    public static int[] renderAndShift(GuiGraphics graphics, int timerX, int timerY,
                                       int timerWidth, int timerHeight, float scale,
                                       int screenWidth, int screenHeight) {
        if (!ClientTimerState.hasTitles()) return new int[]{timerX, timerY};
        Font font = Minecraft.getInstance().font;
        int gap = Math.max(1, (int) (TitleLayout.GAP * scale));

        Component[] titles = new Component[4];
        int[] widths = new int[4];
        int[] heights = new int[4];
        for (int slot = 0; slot < 4; slot++) {
            titles[slot] = ClientTimerState.titleComponent(slot);
            if (titles[slot] == null) continue;
            widths[slot] = (int) (font.width(titles[slot]) * scale);
            heights[slot] = (int) (font.lineHeight * scale);
        }

        timerX += TitleLayout.timerShiftX(timerX, timerWidth, widths, gap, screenWidth);
        timerY += TitleLayout.timerShiftY(timerY, timerHeight, heights, gap, screenHeight);

        for (int slot = 0; slot < 4; slot++) {
            if (titles[slot] == null) continue;
            int x = TitleLayout.posX(slot, timerX, timerWidth, widths, gap, screenWidth);
            int y = TitleLayout.posY(slot, timerY, timerHeight, heights[slot], gap, screenHeight);
            if (scale != 1.0f) {
                PoseStack pose = graphics.pose();
                pose.pushPose();
                pose.translate(x, y, 0);
                pose.scale(scale, scale, 1.0f);
                graphics.drawString(font, titles[slot], 0, 0, 0xFFFFFFFF, true);
                pose.popPose();
            } else {
                graphics.drawString(font, titles[slot], x, y, 0xFFFFFFFF, true);
            }
        }
        return new int[]{timerX, timerY};
    }
}
