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
    private static Runnable opener = null;
    private static java.util.function.Consumer<String> sender = null;

    /**
     * Applies a snapshot. Called on the client thread.
     *
     * <p>Only the first snapshot after {@code /timer gui} carries {@code open},
     * and only that one opens the screen. The heartbeats that follow must not,
     * or closing the panel would fight the next push a second later.</p>
     */
    public static void accept(String json) {
        try {
            state = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            com.mateof24.OnTimeConstants.LOGGER.warn("[OnTime/Admin] unreadable panel state", e);
            return;
        }
        boolean open = state.has("open") && state.get("open").getAsBoolean();
        if (open && opener != null) opener.run();
        else if (listener != null) listener.run();
    }

    /** Registered by the per-version screen, which is the only place that can build one. */
    public static void setOpener(Runnable onOpen) {
        opener = onOpen;
    }

    /** Opens the placement screen for one timer. */
    @FunctionalInterface
    public interface PickerOpener {
        void open(String timerName, int x, int y, float scale, PositionPicker.Save save);
    }

    private static PickerOpener pickerOpener;

    /** Registered alongside {@link #setOpener}, and for the same reason. */
    public static void setPickerOpener(PickerOpener opener) {
        pickerOpener = opener;
    }

    /** Does nothing when no screen registered one, which is every non-client side. */
    public static void openPicker(String timerName, int x, int y, float scale, PositionPicker.Save save) {
        if (pickerOpener != null) pickerOpener.open(timerName, x, y, scale, save);
    }

    /** Registered by the loader, which is the only place that knows how to send. */
    public static void setSender(java.util.function.Consumer<String> onSend) {
        sender = onSend;
    }

    /** Sends one action. Silently does nothing when no loader registered a sender. */
    public static void send(String json) {
        if (sender != null) sender.accept(json);
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
