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

    /** Drops every widget, before laying the panel out again. */
    void clearWidgets();

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
