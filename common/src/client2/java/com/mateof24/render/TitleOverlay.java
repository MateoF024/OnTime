package com.mateof24.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Draws the four optional decorative titles around the timer (4.0.0) and
 * nudges the counter when a title would not fit on-screen: e.g. an 'above'
 * title at a top preset takes the counter's default spot and pushes the
 * counter down instead of overlapping it. Invoked by TimerRenderer before
 * the custom-renderer hook: a custom ITimerRenderer replaces only the
 * counter, the titles stay.
 * 1.21.6/1.21.11 family flavor: GuiGraphics with the Matrix3x2fStack pose introduced in 1.21.6.
 */
public final class TitleOverlay {

    private TitleOverlay() {}

    /**
     * Measures the titles, shifts the timer rect so everything fits, draws
     * the titles around the shifted rect and returns {adjustedX, adjustedY}
     * for the counter itself.
     */
    public static int[] renderAndShift(ClientRunView view, GuiGraphics graphics, int timerX, int timerY,
                                       int timerWidth, int timerHeight, float scale,
                                       int screenWidth, int screenHeight) {
        Font font = Minecraft.getInstance().font;
        TitleBlock block = TitleBlock.of(font, view, timerX, timerY, timerWidth, timerHeight,
                scale, screenWidth, screenHeight);
        if (block == null) return new int[]{timerX, timerY};
        TitleLayout.Placement layout = block.layout;

        for (int slot = 0; slot < 4; slot++) {
            Component title = block.titles[slot];
            if (title == null) continue;
            int x = layout.x[slot];
            int y = layout.y[slot];
            if (scale != 1.0f) {
                var pose = graphics.pose();
                pose.pushMatrix();
                pose.translate(x, y);
                pose.scale(scale, scale);
                graphics.drawString(font, title, 0, 0, 0xFFFFFFFF, true);
                pose.popMatrix();
            } else {
                graphics.drawString(font, title, x, y, 0xFFFFFFFF, true);
            }
        }
        return new int[]{layout.timerX, layout.timerY};
    }
}
