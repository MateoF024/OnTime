package com.mateof24.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Draws the four optional decorative titles around the timer (4.0.0).
 * Invoked by TimerRenderer before the custom-renderer hook: a custom
 * ITimerRenderer replaces only the counter, the titles stay.
 * GuiGraphicsExtractor era (MC 26.x).
 */
public final class TitleOverlay {

    private TitleOverlay() {}

    public static void render(GuiGraphicsExtractor graphics, int timerX, int timerY,
                              int timerWidth, int timerHeight, float scale,
                              int screenWidth, int screenHeight) {
        if (!ClientTimerState.hasTitles()) return;
        Font font = Minecraft.getInstance().font;
        int gap = Math.max(1, (int) (TitleLayout.GAP * scale));
        for (int slot = 0; slot < 4; slot++) {
            Component title = ClientTimerState.titleComponent(slot);
            if (title == null) continue;
            int width = (int) (font.width(title) * scale);
            int height = (int) (font.lineHeight * scale);
            int x = TitleLayout.posX(slot, timerX, timerWidth, width, gap, screenWidth);
            int y = TitleLayout.posY(slot, timerY, timerHeight, height, gap, screenHeight);
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
    }
}
