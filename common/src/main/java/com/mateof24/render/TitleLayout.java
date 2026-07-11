package com.mateof24.render;

/**
 * Pure-math layout for the four decorative title slots around the timer
 * (4.0.0). Deliberately free of Minecraft types so every loader/family
 * renderer (GuiGraphics era and GuiGraphicsExtractor era) shares the exact
 * same placement: above/below are centered horizontally on the counter,
 * left/right are centered vertically, all separated by GAP scaled pixels
 * and clamped to the screen.
 */
public final class TitleLayout {

    /** Unscaled gap in pixels between the counter and a title. */
    public static final int GAP = 2;

    /** Slot indices, shared with ClientTimerState.titleComponent(slot). */
    public static final int ABOVE = 0;
    public static final int BELOW = 1;
    public static final int LEFT = 2;
    public static final int RIGHT = 3;

    private TitleLayout() {}

    public static int posX(int slot, int timerX, int timerWidth,
                           int titleWidth, int gap, int screenWidth) {
        int x = switch (slot) {
            case LEFT -> timerX - gap - titleWidth;
            case RIGHT -> timerX + timerWidth + gap;
            default -> timerX + (timerWidth - titleWidth) / 2;
        };
        return clamp(x, 0, Math.max(0, screenWidth - titleWidth));
    }

    public static int posY(int slot, int timerY, int timerHeight,
                           int titleHeight, int gap, int screenHeight) {
        int y = switch (slot) {
            case ABOVE -> timerY - gap - titleHeight;
            case BELOW -> timerY + timerHeight + gap;
            default -> timerY + (timerHeight - titleHeight) / 2;
        };
        return clamp(y, 0, Math.max(0, screenHeight - titleHeight));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
