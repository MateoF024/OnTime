package com.mateof24.gui;

import com.mateof24.gui.PositionPicker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The screen the position picker runs on. 26.x: GuiGraphicsExtractor, and setScreenAndShow.
 *
 * <p>Deliberately bare: no widgets at all. Every button would sit somewhere
 * the counter might be placed, so the only way out is ESC, which asks. The
 * world keeps rendering behind it because placing a counter against the sky is
 * not the same as placing it against a wall.</p>
 */
public class PositionScreen extends Screen {

    private final PositionPicker picker;
    public PositionScreen(String timerName, int x, int y, float scale, PositionPicker.Save save) {
        super(Component.translatable("ontime.gui.picker.title"));
        this.picker = new PositionPicker(timerName, x, y, scale, save);
    }

    /**
     * Back to the panel it was opened from.
     *
     * <p>A fresh one rather than the instance that opened this: the panel
     * rebuilds itself from the server's snapshot anyway, and holding on to the
     * old screen only differs in the versions where reaching it is awkward.</p>
     */
    private void closeBack() {
        Minecraft.getInstance().setScreenAndShow(new AdminScreen());
    }

    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // No super call: that draws the darkening the menu screens use, and
        // the whole job here is seeing the game exactly as it will look.
        picker.draw(new GfxPainter(graphics), this.width, this.height);
        if (picker.answered() != null) closeBack();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (picker.keyPressed(event.key())) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        if (picker.mouseDown(new GfxPainter(null), event.x(), event.y(), this.width, this.height)) return true;
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        picker.mouseDrag(event.x(), event.y(), this.width, this.height);
        return true;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        picker.mouseUp();
        return super.mouseReleased(event);
    }

    /** ESC is handled by the picker, so vanilla never gets to close this. */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Measuring needs a font and nothing else, so a null graphics is fine for that. */
    private final class GfxPainter implements Painter {

        private final net.minecraft.client.gui.GuiGraphicsExtractor graphics;

        GfxPainter(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
            this.graphics = graphics;
        }

        @Override
        public void text(Component text, int x, int y, int argb) {
            graphics.text(font, text, x, y, argb, true);
        }

        @Override
        public void flatText(String text, int x, int y, int argb) {
            graphics.text(font, text, x, y, argb, false);
        }

        @Override
        public void rect(int x, int y, int width, int height, int argb) {
            graphics.fill(x, y, x + width, y + height, argb);
        }

        @Override
        public void pushScale(float scale, int originX, int originY) {
            PoseScale.push(graphics, scale, originX, originY);
        }

        @Override
        public void popScale() {
            PoseScale.pop(graphics);
        }

        @Override
        public int textWidth(Component text) {
            return font.width(text);
        }

        @Override
        public int lineHeight() {
            return font.lineHeight;
        }
    }
}
