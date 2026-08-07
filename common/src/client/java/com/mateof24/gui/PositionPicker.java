package com.mateof24.gui;

import net.minecraft.network.chat.Component;

/**
 * Placing a timer by dragging it over the running game.
 *
 * <p>Everything here is arithmetic and state. {@link Painter} does the
 * drawing and the per-version screen supplies the input, which is the only
 * part that differs between 1.21.1 and 26.2.</p>
 *
 * <p>Nothing of the mod's own interface is drawn while placing: no header, no
 * rail, and above all no Save and Exit buttons. Buttons would sit on top of
 * the screen and the whole point is that every part of the screen is somewhere
 * the counter might go. Leaving is ESC, and ESC asks.</p>
 */
public final class PositionPicker {

    /** Told where the counter ended up, when the operator chooses to keep it. */
    @FunctionalInterface
    public interface Save {
        void at(int x, int y);
    }

    /** What ESC offers. Cancel is first because it is the one that undoes a mis-key. */
    public enum Choice { CANCEL, DISCARD, SAVE }

    private static final String SAMPLE_TIME = "00:00:00";

    /** Space between the counter and a title beside it. */
    private static final int TITLE_GAP = 4;

    /** Whole steps, and round: the point is to see the size, not to tune it. */
    private static final float MIN_SCALE = 1.0f;
    private static final float MAX_SCALE = 5.0f;

    /** Vanilla draws its overlay message here, so the hint lands where the eye already looks. */
    private static final int ACTION_BAR_FROM_BOTTOM = 68;

    private static final long HINT_FADE_IN_MS = 400L;
    private static final long HINT_HOLD_MS = 6000L;
    private static final long HINT_FADE_OUT_MS = 900L;

    private static final int WHITE = 0xFFFFFF;
    private static final int BOUNDS_RED = 0xFFFF4040;
    private static final int DIALOG_BACK = 0xE8101014;
    private static final int DIALOG_LINE = 0x70FFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFA0A0A8;
    private static final int TEXT_DANGER = 0xFFE06A6A;

    private final String timerName;
    private final String preset;
    private final String timeText;
    /** above, below, left, right. A blank entry is a title this timer does not have. */
    private final String[] titles;
    private final Save save;
    /** Until the first draw the screen size is unknown, so a preset cannot be resolved. */
    private boolean placed;
    private final long openedAt = System.currentTimeMillis();

    private int x;
    private int y;
    private boolean dragging;
    private int grabX;
    private int grabY;

    private boolean showTitles;
    private float previewScale = 1.0f;

    /** While true the placement is frozen and the three choices are on screen. */
    private boolean asking;

    public PositionPicker(String timerName, String preset, int x, int y, float scale,
                          String timeText, String[] titles, Save save) {
        this.timerName = timerName;
        this.preset = preset == null ? "CUSTOM" : preset;
        this.x = x;
        this.y = y;
        this.previewScale = clampScale(scale);
        this.timeText = timeText == null || timeText.isEmpty() ? "00:00:00" : timeText;
        this.titles = titles == null ? new String[4] : titles;
        this.save = save;
        this.placed = "CUSTOM".equalsIgnoreCase(this.preset);
    }

    /**
     * Starts where the counter is now, not in the corner.
     *
     * <p>Only CUSTOM keeps coordinates of its own; every other preset works
     * its anchor out from the screen. Opening on the stored x and y meant
     * opening on {@code -1, 4} — the corner — for anybody who had never used
     * CUSTOM, which is everybody the first time.</p>
     */
    private void placeFromPreset(Painter painter, int screenW, int screenH) {
        if (placed) return;
        placed = true;
        int[] local = localBox(painter);
        int w = Math.round(local[2] * previewScale);
        int h = Math.round(local[3] * previewScale);

        com.mateof24.config.TimerPositionPreset resolved;
        try {
            resolved = com.mateof24.config.TimerPositionPreset.valueOf(
                    preset.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return;
        }
        int px = resolved.calculateX(screenW, w, x);
        int py = resolved.calculateY(screenH, h, y);
        // -1 is the enum's way of saying "centred", which only the caller can work out.
        x = px == -1 ? (screenW - w) / 2 : px;
        y = py == -1 ? (screenH - h) / 2 : py;
        // The box is measured from the counter, so undo the titles' overhang.
        x -= Math.round(local[0] * previewScale);
        y -= Math.round(local[1] * previewScale);
    }

    private boolean hasTitle(int slot) {
        return titles.length > slot && titles[slot] != null && !titles[slot].isEmpty();
    }

    private Component titleAt(int slot) {
        return Component.literal(titles[slot]);
    }

    private boolean anyTitle() {
        for (int i = 0; i < 4; i++) if (hasTitle(i)) return true;
        return false;
    }

    private static float clampScale(float value) {
        float rounded = Math.round(value);
        if (rounded < MIN_SCALE) return MIN_SCALE;
        if (rounded > MAX_SCALE) return MAX_SCALE;
        return rounded;
    }

    public boolean asking() { return asking; }

    // ---- what the counter takes up ----

    /**
     * The overlay in unscaled pixels, relative to the counter's top-left.
     *
     * <p>{@code {left, top, width, height}}. Everything — the red box, the
     * titles, the hit test — is derived from this one rectangle and then run
     * through the same scale, which is what keeps them agreeing. Computing the
     * box separately from the drawing is what let it drift by a pixel at the
     * bottom and by a whole title at scale three.</p>
     */
    private int[] localBox(Painter painter) {
        int line = painter.lineHeight();
        int glyphs = glyphHeight(painter);
        int width = painter.textWidth(Component.literal(timeText));
        int left = 0;
        int top = 0;
        int right = width;
        int bottom = glyphs;

        if (showTitles) {
            if (hasTitle(0)) top -= line;
            if (hasTitle(1)) bottom += line;
            if (hasTitle(2)) left -= painter.textWidth(titleAt(2)) + TITLE_GAP;
            if (hasTitle(3)) right += painter.textWidth(titleAt(3)) + TITLE_GAP;
        }
        return new int[]{left, top, right - left, bottom - top};
    }

    /**
     * The ink, not the line box.
     *
     * <p>A font line is taller than the glyphs in it: the descender row is
     * empty for digits, so measuring the box with the line height left one
     * blank pixel along the bottom and nowhere else.</p>
     */
    private static int glyphHeight(Painter painter) {
        return painter.lineHeight() - 1;
    }

    /** The box on screen, which is the local one scaled about the counter. */
    private int[] bounds(Painter painter) {
        int[] local = localBox(painter);
        return new int[]{
                x + Math.round(local[0] * previewScale),
                y + Math.round(local[1] * previewScale),
                Math.round(local[2] * previewScale),
                Math.round(local[3] * previewScale)};
    }

    // ---- input ----

    /** True when the click was taken, so the screen leaves it alone. */
    public boolean mouseDown(Painter painter, double mouseX, double mouseY, int screenW, int screenH) {
        // The three buttons are widgets and vanilla routes to them first;
        // swallowing the rest keeps a click beside the dialog from grabbing
        // the counter hidden behind it.
        if (asking) return true;
        int[] box = bounds(painter);
        if (mouseX < box[0] || mouseX > box[0] + box[2]
                || mouseY < box[1] || mouseY > box[1] + box[3]) {
            return false;
        }
        dragging = true;
        grabX = (int) mouseX - x;
        grabY = (int) mouseY - y;
        return true;
    }

    public void mouseDrag(double mouseX, double mouseY, int screenW, int screenH) {
        if (!dragging || asking) return;
        x = (int) mouseX - grabX;
        y = (int) mouseY - grabY;
    }

    public void mouseUp() { dragging = false; }

    /**
     * @return true when the key was ours, so the screen does not also act on it
     */
    public boolean keyPressed(int keyCode) {
        if (keyCode == 256) { // ESC
            if (asking) { asking = false; return true; }
            asking = true;
            dragging = false;
            return true;
        }
        if (asking) return false;
        if (keyCode == 84) { // T
            // Nothing to toggle when this timer has no titles: showing four
            // samples would be showing something that is not there.
            if (!anyTitle()) return true;
            showTitles = !showTitles;
            return true;
        }
        if (keyCode == 83) { // S
            previewScale = previewScale >= MAX_SCALE ? MIN_SCALE : previewScale + 1.0f;
            return true;
        }
        return false;
    }

    /** Applied by the screen, which is the only thing that can close itself. */
    private Choice answered;

    private void answer(Choice choice) {
        if (choice == Choice.CANCEL) {
            asking = false;
            return;
        }
        if (choice == Choice.SAVE) save.at(x, y);
        answered = choice;
    }

    /** Non-null once the operator has chosen to leave; the screen then closes. */
    public Choice answered() { return answered; }

    // ---- drawing ----

    public void draw(Painter painter, int screenW, int screenH) {
        placeFromPreset(painter, screenW, screenH);
        drawCounter(painter);
        drawBounds(painter);
        drawHint(painter, screenW, screenH);
        if (asking) drawDialog(painter, screenW, screenH);
    }

    private void drawCounter(Painter painter) {
        int[] local = localBox(painter);
        int width = painter.textWidth(Component.literal(timeText));
        int line = painter.lineHeight();

        painter.pushScale(previewScale, x, y);
        painter.text(Component.literal(timeText), x, y, TEXT);
        if (showTitles) {
            // Centred over the whole overlay, not over the counter: with a
            // title on one side only, the two are not the same rectangle.
            int centre = x + local[0] + local[2] / 2;
            if (hasTitle(0)) {
                painter.text(titleAt(0), centre - painter.textWidth(titleAt(0)) / 2, y - line, TEXT);
            }
            if (hasTitle(1)) {
                painter.text(titleAt(1), centre - painter.textWidth(titleAt(1)) / 2, y + line, TEXT);
            }
            if (hasTitle(2)) {
                painter.text(titleAt(2), x - painter.textWidth(titleAt(2)) - TITLE_GAP, y, TEXT);
            }
            if (hasTitle(3)) painter.text(titleAt(3), x + width + TITLE_GAP, y, TEXT);
        }
        painter.popScale();
    }

    /** Only while it is being moved: a permanent red box is noise. */
    private void drawBounds(Painter painter) {
        if (!dragging) return;
        int[] box = bounds(painter);
        painter.outline(box[0] - 1, box[1] - 1, box[2] + 2, box[3] + 2, BOUNDS_RED);
    }

    /**
     * The one instruction, and it leaves.
     *
     * <p>At the height vanilla puts its own overlay message, white with the
     * shadow the rest of the interface uses, faded in and out so it reads as a
     * notice rather than as part of what is being placed.</p>
     */
    private void drawHint(Painter painter, int screenW, int screenH) {
        if (asking) return;
        long elapsed = System.currentTimeMillis() - openedAt;
        long total = HINT_FADE_IN_MS + HINT_HOLD_MS + HINT_FADE_OUT_MS;
        if (elapsed >= total) return;

        float alpha;
        if (elapsed < HINT_FADE_IN_MS) {
            alpha = elapsed / (float) HINT_FADE_IN_MS;
        } else if (elapsed < HINT_FADE_IN_MS + HINT_HOLD_MS) {
            alpha = 1f;
        } else {
            alpha = 1f - (elapsed - HINT_FADE_IN_MS - HINT_HOLD_MS) / (float) HINT_FADE_OUT_MS;
        }
        int a = Math.max(0, Math.min(255, (int) (alpha * 255f)));
        if (a == 0) return;

        Component hint = Component.translatable("ontime.gui.picker.hint");
        painter.text(hint, (screenW - painter.textWidth(hint)) / 2,
                screenH - ACTION_BAR_FROM_BOTTOM, (a << 24) | WHITE);
    }

    /**
     * Title and body only. The three buttons are vanilla widgets the screen
     * adds, like every other dialog in this interface — hand-drawn lookalikes
     * were the one place that did not match.
     */
    private void drawDialog(Painter painter, int screenW, int screenH) {
        int width = 300;
        int height = 92;
        int left = (screenW - width) / 2;
        int top = (screenH - height) / 2;

        painter.rect(left, top, width, height, DIALOG_BACK);
        painter.outline(left, top, width, height, DIALOG_LINE);

        Component title = Component.translatable("ontime.gui.picker.exit.title");
        painter.text(title, left + (width - painter.textWidth(title)) / 2, top + 14, TEXT);
        Component body = Component.translatable("ontime.gui.picker.exit.body", timerName);
        painter.text(body, left + (width - painter.textWidth(body)) / 2, top + 32, TEXT_DIM);
    }

    /** Where the screen puts the three buttons, so both agree on one rectangle. */
    public int dialogLeft(int screenW) { return (screenW - 300) / 2; }

    public int dialogWidth() { return 300; }

    public int dialogButtonsY(int screenH) { return (screenH - 92) / 2 + 92 - 30; }

    /** Called by the buttons. */
    public void choose(Choice choice) { answer(choice); }
}
