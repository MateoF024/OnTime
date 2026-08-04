package com.mateof24.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Draws the four optional decorative titles around the timer (4.0.0) and
 * nudges the counter when a title would not fit on-screen: e.g. an 'above'
 * title at a top preset takes the counter's default spot and pushes the
 * counter down instead of overlapping it. Invoked by TimerRenderer before
 * the custom-renderer hook: a custom ITimerRenderer replaces only the
 * counter, the titles stay.
 * 26.x family flavor: GuiGraphicsExtractor with the Component text overload.
 *
 * <p>SYNC NOTE: byte-identical to the client4 copy. The clientVer split exists
 * only because TimerRenderer differs between 26.1 and 26.2 (Options.hideGui vs
 * Hud.isHidden), not because this file does. Edit both, or split them properly
 * the day 26.2 actually diverges here.</p>
 */
public final class TitleOverlay {

    private TitleOverlay() {}

    /**
     * Measures the titles, shifts the timer rect so everything fits, draws
     * the titles around the shifted rect and returns {adjustedX, adjustedY}
     * for the counter itself.
     */
    public static int[] renderAndShift(ClientRunView view, GuiGraphicsExtractor graphics, int timerX, int timerY,
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
                graphics.text(font, title, 0, 0, 0xFFFFFFFF, true);
                pose.popMatrix();
            } else {
                graphics.text(font, title, x, y, 0xFFFFFFFF, true);
            }
        }
        return new int[]{layout.timerX, layout.timerY};
    }
}
