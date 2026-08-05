package com.mateof24.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The administration panel's screen.
 *
 * <p>MC 1.21.10 and 1.21.11: still GuiGraphics, but input arrives as KeyEvent and MouseButtonEvent.</p>
 *
 * <p><b>What lives here and nothing else.</b> Vanilla's input signatures
 * changed shape at 1.21.10 — {@code keyPressed} and {@code mouseClicked} took
 * event objects — and the drawing type changed again at 26.1. Those two facts
 * are the entire reason this file exists three times. Everything else about
 * the panel is written once in {@code common/src/client}: this hands
 * {@link AdminPanel} a {@link Painter} and a {@link PanelHost} and forwards
 * the lifecycle.</p>
 *
 * <p>Input goes to the panel before {@code super}, because the completion list
 * is drawn rather than built out of widgets: vanilla does not know it is there
 * and would hand the click to whatever is underneath it.</p>
 */
public class AdminScreen extends Screen implements PanelHost {

    private final AdminPanel panel = new AdminPanel(this);

    public AdminScreen() {
        super(Component.translatable("ontime.gui.title"));
    }

    /** Lets {@link AdminClientState} open this screen without naming the class. */
    public static void register() {
        AdminClientState.setOpener(() -> Minecraft.getInstance().setScreen(new AdminScreen()));
    }

    @Override
    protected void init() {
        panel.refresh(AdminClientState.get());
        panel.init();
        // A snapshot landing while the panel is open reloads the data and lays
        // it out again, so another admin's changes appear without a keypress.
        AdminClientState.setListener(() -> panel.onSnapshot(AdminClientState.get()));
    }

    /**
     * Bands first, then the screen, then the text.
     *
     * <p>The order is the whole trick. Anything filled that is drawn after
     * {@code super} lands on top of every button and greys the lot — which is
     * exactly what happened the first time this was written. It is also what
     * lets the completion list be drawn without lifting it in z, which is the
     * one thing that could not have been written once.</p>
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Painter painter = new GfxPainter(graphics);
        panel.drawBands(painter, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        panel.drawContent(painter);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (panel.keyPressed(event.key())) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        if (panel.mouseClicked(event.x(), event.y())) return true;
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (panel.mouseScrolled(deltaY)) return true;
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }


    @Override
    public void onClose() {
        panel.onClosed();
        AdminClientState.setListener(null);
        super.onClose();
    }

    /** The world keeps running behind it: this is a server tool, not a menu. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- PanelHost ----

    @Override
    public <T extends AbstractWidget> T addWidget(T widget) {
        return addRenderableWidget(widget);
    }

    @Override
    public void clearWidgets() {
        super.clearWidgets();
    }

    @Override
    public void closePanel() {
        onClose();
    }

    @Override
    public int panelWidth() {
        return this.width;
    }

    @Override
    public int panelHeight() {
        return this.height;
    }

    @Override
    public Font font() {
        return Minecraft.getInstance().font;
    }

    @Override
    public void sendAction(String json) {
        AdminClientState.send(json);
    }

    /** GuiGraphics is the only drawing type that changed across the range. */
    private final class GfxPainter implements Painter {

        private final GuiGraphics graphics;

        GfxPainter(GuiGraphics graphics) {
            this.graphics = graphics;
        }

        @Override
        public void text(Component text, int x, int y, int argb) {
            graphics.drawString(font(), text, x, y, argb, true);
        }

        @Override
        public void flatText(String text, int x, int y, int argb) {
            graphics.drawString(font(), text, x, y, argb, false);
        }

        @Override
        public void rect(int x, int y, int width, int height, int argb) {
            graphics.fill(x, y, x + width, y + height, argb);
        }

        @Override
        public int textWidth(Component text) {
            return font().width(text);
        }

        @Override
        public int lineHeight() {
            return font().lineHeight;
        }
    }
}
