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

    /** Whole steps, and round: the point is to see the size, not to tune it. */
    private static final float MIN_SCALE = 1.0f;
    private static final float MAX_SCALE = 5.0f;

    /** Vanilla draws its overlay message here, so the hint lands where the eye already looks. */
    private static final int ACTION_BAR_FROM_BOTTOM = 68;

    private static final long HINT_FADE_IN_MS = 400L;
    private static final long HINT_HOLD_MS = 3200L;
    private static final long HINT_FADE_OUT_MS = 700L;

    private static final int WHITE = 0xFFFFFF;
    private static final int BOUNDS_RED = 0xFFFF4040;
    private static final int DIALOG_BACK = 0xE8101014;
    private static final int DIALOG_LINE = 0x70FFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFA0A0A8;
    private static final int TEXT_DANGER = 0xFFE06A6A;

    private final String timerName;
    private final Save save;
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
    private final int[] choiceX = new int[3];
    private final int[] choiceW = new int[3];
    private int choiceY;
    private int choiceH;

    public PositionPicker(String timerName, int x, int y, float scale, Save save) {
        this.timerName = timerName;
        this.x = x;
        this.y = y;
        this.previewScale = clampScale(scale);
        this.save = save;
    }

    private static float clampScale(float value) {
        float rounded = Math.round(value);
        if (rounded < MIN_SCALE) return MIN_SCALE;
        if (rounded > MAX_SCALE) return MAX_SCALE;
        return rounded;
    }

    public boolean asking() { return asking; }

    // ---- what the counter takes up ----

    private int textWidth(Painter painter) {
        return (int) (painter.textWidth(Component.literal(SAMPLE_TIME)) * previewScale);
    }

    private int textHeight(Painter painter) {
        return (int) (painter.lineHeight() * previewScale);
    }

    /**
     * The whole overlay, titles included when they are showing.
     *
     * <p>Adaptive on purpose: a counter with text above and below is a
     * different shape from a bare one, and placing it against the bottom of
     * the screen with the titles hidden is how you find out afterwards that
     * the lower title was off-screen all along.</p>
     */
    private int[] bounds(Painter painter) {
        int w = textWidth(painter);
        int h = textHeight(painter);
        int left = x;
        int top = y;
        int right = x + w;
        int bottom = y + h;

        if (showTitles) {
            int line = (int) (painter.lineHeight() * previewScale);
            int side = (int) (painter.textWidth(sampleTitle()) * previewScale);
            top -= line + 2;
            bottom += line + 2;
            left -= side + 4;
            right += side + 4;
        }
        return new int[]{left, top, right - left, bottom - top};
    }

    private static Component sampleTitle() {
        return Component.translatable("ontime.gui.picker.title.sample");
    }

    // ---- input ----

    /** True when the click was taken, so the screen leaves it alone. */
    public boolean mouseDown(Painter painter, double mouseX, double mouseY, int screenW, int screenH) {
        if (asking) {
            Choice hit = choiceAt(mouseX, mouseY);
            if (hit != null) answer(hit);
            return true;
        }
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
            showTitles = !showTitles;
            return true;
        }
        if (keyCode == 83) { // S
            previewScale = previewScale >= MAX_SCALE ? MIN_SCALE : previewScale + 1.0f;
            return true;
        }
        return false;
    }

    private Choice choiceAt(double mouseX, double mouseY) {
        if (mouseY < choiceY || mouseY > choiceY + choiceH) return null;
        for (int i = 0; i < 3; i++) {
            if (mouseX >= choiceX[i] && mouseX <= choiceX[i] + choiceW[i]) return Choice.values()[i];
        }
        return null;
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
        drawCounter(painter);
        drawBounds(painter);
        drawHint(painter, screenW, screenH);
        if (asking) drawDialog(painter, screenW, screenH);
    }

    private void drawCounter(Painter painter) {
        painter.pushScale(previewScale, x, y);
        painter.text(Component.literal(SAMPLE_TIME), x, y, TEXT);
        if (showTitles) {
            int line = painter.lineHeight();
            int width = painter.textWidth(Component.literal(SAMPLE_TIME));
            int side = painter.textWidth(sampleTitle());
            painter.text(sampleTitle(), x, y - line - 2, TEXT);
            painter.text(sampleTitle(), x, y + line + 2, TEXT);
            painter.text(sampleTitle(), x - side - 4, y, TEXT);
            painter.text(sampleTitle(), x + width + 4, y, TEXT);
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

    private void drawDialog(Painter painter, int screenW, int screenH) {
        int width = 300;
        int height = 92;
        int left = (screenW - width) / 2;
        int top = (screenH - height) / 2;

        painter.rect(left, top, width, height, DIALOG_BACK);
        painter.outline(left, top, width, height, DIALOG_LINE);

        Component title = Component.translatable("ontime.gui.picker.exit.title");
        painter.text(title, left + (width - painter.textWidth(title)) / 2, top + 12, TEXT);
        Component body = Component.translatable("ontime.gui.picker.exit.body", timerName);
        painter.text(body, left + (width - painter.textWidth(body)) / 2, top + 28, TEXT_DIM);

        Component[] labels = {
                Component.translatable("gui.cancel"),
                Component.translatable("ontime.gui.picker.exit.discard"),
                Component.translatable("ontime.gui.picker.exit.save")
        };
        int[] colours = {TEXT, TEXT_DANGER, TEXT};

        choiceY = top + height - 30;
        choiceH = 20;
        int gap = 8;
        int each = (width - 24 - 2 * gap) / 3;
        for (int i = 0; i < 3; i++) {
            choiceX[i] = left + 12 + i * (each + gap);
            choiceW[i] = each;
            painter.rect(choiceX[i], choiceY, each, choiceH, 0x40FFFFFF);
            painter.outline(choiceX[i], choiceY, each, choiceH, DIALOG_LINE);
            painter.text(labels[i], choiceX[i] + (each - painter.textWidth(labels[i])) / 2,
                    choiceY + 6, colours[i]);
        }
    }
}
