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

    /**
     * X of one title. widths[] holds every slot's width (0 = unset): the
     * above/below titles center over the WHOLE left+counter+right block,
     * not just the counter, so the composition reads as one unit.
     */
    public static int posX(int slot, int timerX, int timerWidth,
                           int[] widths, int gap, int screenWidth) {
        int titleWidth = widths[slot];
        int x;
        if (slot == LEFT) {
            x = timerX - gap - titleWidth;
        } else if (slot == RIGHT) {
            x = timerX + timerWidth + gap;
        } else {
            int leftExtent = widths[LEFT] > 0 ? widths[LEFT] + gap : 0;
            int rightExtent = widths[RIGHT] > 0 ? widths[RIGHT] + gap : 0;
            int blockX = timerX - leftExtent;
            int blockWidth = leftExtent + timerWidth + rightExtent;
            x = blockX + (blockWidth - titleWidth) / 2;
        }
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

    /**
     * Horizontal shift for the TIMER. The side titles and the counter form
     * ONE unit: the counter first shifts by (leftExtent - rightExtent)/2 so
     * the whole left+counter+right block stays centered on the spot the
     * counter alone occupied (bossbar/actionbar/center presets keep looking
     * centered). Then the block is kept on-screen: a left title at the left
     * edge pushes it right, a right title at the right edge pushes it left.
     * widths[] is indexed by slot, 0 = slot unset.
     */
    public static int timerShiftX(int timerX, int timerWidth, int[] widths, int gap, int screenWidth) {
        int leftExtent = widths[LEFT] > 0 ? widths[LEFT] + gap : 0;
        int rightExtent = widths[RIGHT] > 0 ? widths[RIGHT] + gap : 0;
        int shift = (leftExtent - rightExtent) / 2;
        if (leftExtent > 0 && timerX + shift < leftExtent) {
            shift = leftExtent - timerX;
        }
        if (rightExtent > 0) {
            int overflow = (timerX + shift + timerWidth + rightExtent) - screenWidth;
            if (overflow > 0) shift -= overflow;
        }
        return shift;
    }

    /**
     * Vertical shift for the TIMER: an 'above' title at a top preset takes
     * the counter's spot and pushes the counter down (and 'below' at the
     * bottom edge pushes it up). The title always keeps a small breathing
     * margin (2×gap) from the window edge instead of touching it.
     * heights[] is indexed by slot, 0 = unset.
     */
    public static int timerShiftY(int timerY, int timerHeight, int[] heights, int gap, int screenHeight) {
        int edgeMargin = gap * 2;
        int shift = 0;
        if (heights[ABOVE] > 0) {
            int needed = edgeMargin + heights[ABOVE] + gap;
            if (timerY < needed) shift = needed - timerY;
        }
        if (heights[BELOW] > 0) {
            int needed = heights[BELOW] + gap + edgeMargin;
            int overflow = (timerY + shift + timerHeight + needed) - screenHeight;
            if (overflow > 0) shift -= overflow;
        }
        return shift;
    }
}
