package com.mateof24.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The last admin snapshot this client received.
 *
 * <p>Pure data with a listener: the screen that draws it arrives in the next
 * phase, and keeping the two apart means the transport can be finished and
 * tested before a single widget exists.</p>
 *
 * <p>The client holds no authority over any of this. It is a picture of what
 * the server said, redrawn each time the server says it again; pressing
 * anything sends an action back and waits to be told the outcome.</p>
 */
public final class AdminClientState {

    private AdminClientState() {}

    private static JsonObject state = null;
    private static Runnable listener = null;

    /** Applies a snapshot. Called on the client thread. */
    public static void accept(String json) {
        try {
            state = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            com.mateof24.OnTimeConstants.LOGGER.warn("[OnTime/Admin] unreadable panel state", e);
            return;
        }
        if (listener != null) listener.run();
    }

    /** The current snapshot, or null when no panel has been opened this session. */
    public static JsonObject get() {
        return state;
    }

    public static boolean hasState() {
        return state != null;
    }

    /** Called whenever a new snapshot lands, so an open screen can redraw. */
    public static void setListener(Runnable onUpdate) {
        listener = onUpdate;
    }

    /** Dropped on disconnect: this is server state and it does not outlive the connection. */
    public static void clear() {
        state = null;
        listener = null;
    }
}
