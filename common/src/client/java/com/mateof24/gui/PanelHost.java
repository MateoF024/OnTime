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
}
