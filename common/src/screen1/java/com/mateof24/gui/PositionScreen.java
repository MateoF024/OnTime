package com.mateof24.gui;

import com.mateof24.gui.PositionPicker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The screen the position picker runs on. MC 1.21.1 to 1.21.9: GuiGraphics, input as loose primitives.
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
        Minecraft.getInstance().setScreen(new AdminScreen());
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No super.render: that draws the darkening the menu screens use, and
        // the whole job here is seeing the game exactly as it will look.
        picker.draw(new GfxPainter(graphics), this.width, this.height);
        if (picker.answered() != null) closeBack();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (picker.keyPressed(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (picker.mouseDown(new GfxPainter(null), mouseX, mouseY, this.width, this.height)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        picker.mouseDrag(mouseX, mouseY, this.width, this.height);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        picker.mouseUp();
        return super.mouseReleased(mouseX, mouseY, button);
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

        private final net.minecraft.client.gui.GuiGraphics graphics;

        GfxPainter(net.minecraft.client.gui.GuiGraphics graphics) {
            this.graphics = graphics;
        }

        @Override
        public void text(Component text, int x, int y, int argb) {
            graphics.drawString(font, text, x, y, argb, true);
        }

        @Override
        public void flatText(String text, int x, int y, int argb) {
            graphics.drawString(font, text, x, y, argb, false);
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
