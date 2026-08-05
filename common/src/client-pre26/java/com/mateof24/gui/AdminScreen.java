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
 * <p>MC 1.21.1 to 1.21.11: {@code GuiGraphics} and {@code render}.</p>
 *
 * <p><b>Why this file overrides so little.</b> Input signatures drift: 1.21.10
 * turned {@code mouseClicked} and {@code keyPressed} into event objects,
 * while 1.21.6 still takes loose primitives — and the {@code v1.21.6} family
 * compiles Fabric against 1.21.10 and NeoForge against 1.21.6, so a single file
 * shared by both cannot override either one. The panel therefore takes its
 * clicks through {@code Button}, whose builder is identical on every version,
 * and this file overrides only what is genuinely stable: {@code init},
 * the render hook, {@code mouseScrolled} and {@code onClose}.</p>
 *
 * <p>Everything else it does is adapt: it hands {@link AdminPanel} a
 * {@link Painter} and a {@link PanelHost} and forwards the lifecycle. The
 * panel — layout, drawing, actions — is written once in
 * {@code common/src/client}.</p>
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
        AdminClientState.setListener(() -> {
            panel.refresh(AdminClientState.get());
            panel.init();
        });
    }

    /**
     * Bands first, then the screen, then the text.
     *
     * <p>The order is the whole trick. Anything filled that is drawn after
     * {@code super} lands on top of every button and greys the lot — which is
     * exactly what happened the first time this was written.</p>
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Painter painter = new GfxPainter(graphics);
        panel.drawBands(painter);
        super.render(graphics, mouseX, mouseY, partialTick);
        panel.drawContent(painter);
    }

    /** The one input override that is identical on every version in range. */
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
