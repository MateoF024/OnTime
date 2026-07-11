package com.mateof24.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Draws the four optional decorative titles around the timer (4.0.0).
 * Invoked by TimerRenderer before the custom-renderer hook: a custom
 * ITimerRenderer replaces only the counter, the titles stay.
 * 1.21.6/1.21.11 family flavor: GuiGraphics with the Matrix3x2fStack
 * pose introduced in 1.21.6 (1.21.1 keeps PoseStack, 26.x moved to
 * GuiGraphicsExtractor — each family source set carries its own copy).
 */
public final class TitleOverlay {

    private TitleOverlay() {}

    public static void render(GuiGraphics graphics, int timerX, int timerY,
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
                graphics.drawString(font, title, 0, 0, 0xFFFFFFFF, true);
                pose.popMatrix();
            } else {
                graphics.drawString(font, title, x, y, 0xFFFFFFFF, true);
            }
        }
    }
}
