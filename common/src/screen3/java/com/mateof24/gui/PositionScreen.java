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
    private final Screen parent;
    public PositionScreen(Screen parent, String timerName, String preset, int x, int y,
                          float scale, String timeText, String[] titles,
                          PositionPicker.Save save) {
        super(Component.translatable("ontime.gui.picker.title"));
        this.parent = parent;
        com.mateof24.render.ClientTimerState.setPlacing(true);
        this.picker = new PositionPicker(timerName, preset, x, y, scale, timeText, titles, save);
    }

    /**
     * Back to the panel it was opened from.
     *
     * <p>The very screen that opened it, not a new one. A new panel starts on
     * the first tab with nothing pending, so what was placed here never
     * survived the trip back.</p>
     */
    private void closeBack() {
        com.mateof24.render.ClientTimerState.setPlacing(false);
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        picker.draw(new GfxPainter(graphics), this.width, this.height);
        // Then the widgets, on top. Skipping this is what left the ESC dialog
        // with a title and no buttons: super is what draws them, and with the
        // background overridden to nothing there is no longer a reason to
        // avoid it.
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (picker.answered() != null) closeBack();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (picker.keyPressed(event.key())) { rebuildWidgets(); return true; }
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

    /**
     * No background at all: no blur, no dark wash, nothing.
     *
     * <p>Overriding render was not enough. The game does not call it directly —
     * it calls a final wrapper (extractRenderStateWithTooltipAndSubtitles) which paints the
     * background first, and the background is what blurs. Suppressing it means
     * overriding this, which is the method that wrapper reaches.</p>
     */
    @Override
    public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    /**
     * The three choices, as vanilla buttons.
     *
     * <p>Built only while ESC is asking, so the placement itself stays clear of
     * widgets. They are real {@code Button}s rather than drawn lookalikes for
     * the same reason every other dialog in this interface uses them: they are
     * what the rest of the game looks like, and they come with focus, keyboard
     * navigation and the sounds for free.</p>
     */
    @Override
    protected void init() {
        clearWidgets();
        if (!picker.asking()) return;

        int left = picker.dialogLeft(this.width);
        int width = picker.dialogWidth();
        int y = picker.dialogButtonsY(this.height);
        int gap = 8;
        int each = (width - 24 - 2 * gap) / 3;

        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        net.minecraft.network.chat.Component.translatable("gui.cancel"),
                        b -> { picker.choose(PositionPicker.Choice.CANCEL); rebuildWidgets(); })
                .bounds(left + 12, y, each, 20).build());

        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        net.minecraft.network.chat.Component.translatable("ontime.gui.picker.exit.discard"),
                        b -> picker.choose(PositionPicker.Choice.DISCARD))
                .bounds(left + 12 + each + gap, y, each, 20).build());

        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        net.minecraft.network.chat.Component.translatable("ontime.gui.picker.exit.save"),
                        b -> picker.choose(PositionPicker.Choice.SAVE))
                .bounds(left + 12 + 2 * (each + gap), y, each, 20).build());
    }

    /** ESC is handled by the picker, so vanilla never gets to close this. */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
        com.mateof24.render.ClientTimerState.setPlacing(false);
        super.onClose();
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
        public void tooltip(Component text, int mouseX, int mouseY) {
            Tooltips.show(graphics, font, text, mouseX, mouseY);
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
