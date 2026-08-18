package com.mateof24.gui;

import net.minecraft.client.gui.components.AbstractWidget;

/**
 * What the panel needs from the {@code Screen} it lives in.
 *
 * <p>The screen subclass differs per Minecraft version; the panel does not. So
 * the panel talks to its host through this, and the host is the only thing that
 * has to be written three times.</p>
 */
public interface PanelHost {

    /** Registers a widget so the screen draws it and routes input to it. */
    <T extends AbstractWidget> T addWidget(T widget);

    /**
     * Drops every widget, before laying the panel out again.
     *
     * <p>Deliberately not called {@code clearWidgets}. Forge 1.20.1 reobfuscates
     * the mod into SRG names, and it matches by name and descriptor: an
     * interface method of this mod's own that happened to read
     * {@code clearWidgets()V}, exactly like {@link net.minecraft.client.gui.screens.Screen}'s,
     * had its call sites rewritten to {@code m_169413_} while the declaration
     * here kept its name. The panel then died laying itself out -- silently on
     * the way in, leaving a screen with no buttons on it, and loudly on every
     * resize. Any name vanilla does not also use is safe.</p>
     */
    void dropWidgets();

    /**
     * Makes this box behave like the one on a command block.
     *
     * <p>Here rather than in the panel because the control is vanilla's, and
     * the three calls that drive it — render, key, click — are the three that
     * change shape between versions. The screen already has those.</p>
     *
     * @param box the box, or null when the page has no command on it
     */
    void bindCommandField(net.minecraft.client.gui.components.EditBox box);

    /** Recomputes the completions, from the box's own responder. */
    void refreshCommandField();

    /**
     * Takes the focus off whatever holds it.
     *
     * <p>A widget cannot do this for itself: the screen keeps its own idea of
     * which child is focused, and clearing the flag on the widget alone left
     * the screen still pointing at it — so clicking that box again was
     * clicking the box the screen already believed was focused, and nothing
     * happened.</p>
     */
    void dropFocus();

    /** Closes the panel, which also unsubscribes from the server's updates. */
    void closePanel();

    int panelWidth();

    int panelHeight();

    net.minecraft.client.gui.Font font();

    /**
     * Sends one action to the server.
     *
     * @param json {@code {"op": "...", "args": {...}}}; the server authorises
     *             and validates it, and nothing here assumes it will succeed
     */
    void sendAction(String json);

    /**
     * Opens the placement screen and comes back to <em>this</em> screen.
     *
     * <p>It used to open through a static hook that returned by building a new
     * panel. A new panel is a new tab and, worse, an empty pending map: the
     * position the picker had just written was thrown away before Apply could
     * ever be pressed, which is why nothing it did ever stuck.</p>
     */
    void openPicker(String timerName, String preset, int x, int y, float scale,
                    String timeText, String[] titles, PositionPicker.Save save);
}
