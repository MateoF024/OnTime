package com.mateof24.render;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * The measured, laid-out title block around one counter.
 *
 * <p>Measuring the titles with the font and then resolving their geometry used
 * to be written out three times — in the renderer, in the boss-bar
 * displacement and in the Jade hook — and all three had to agree exactly or
 * the overlays would fight each other. They all go through here now; the
 * geometry itself lives in {@link TitleLayout}, which has no Minecraft types.</p>
 */
public final class TitleBlock {

    /** Per-slot component, null for an unset slot. Indexed like {@link TitleLayout#ABOVE}. */
    public final Component[] titles;
    public final TitleLayout.Placement layout;

    private TitleBlock(Component[] titles, TitleLayout.Placement layout) {
        this.titles = titles;
        this.layout = layout;
    }

    /**
     * Measures and lays out one execution's titles.
     *
     * @return null when that execution has no titles, in which case callers
     *         keep the counter rect they already had
     */
    public static TitleBlock of(Font font, ClientRunView view, int timerX, int timerY,
                                int timerWidth, int timerHeight, float scale,
                                int screenWidth, int screenHeight) {
        if (view == null || !view.hasTitles()) return null;

        int gap = Math.max(1, (int) (TitleLayout.GAP * scale));
        Component[] titles = new Component[4];
        int[] widths = new int[4];
        int[] heights = new int[4];
        for (int slot = 0; slot < 4; slot++) {
            titles[slot] = view.titleComponent(slot);
            if (titles[slot] == null) continue;
            widths[slot] = (int) (font.width(titles[slot]) * scale);
            heights[slot] = (int) (font.lineHeight * scale);
        }

        return new TitleBlock(titles, TitleLayout.resolve(timerX, timerY, timerWidth, timerHeight,
                widths, heights, gap, screenWidth, screenHeight));
    }

    /**
     * Where one execution's counter and titles end up on screen, as
     * {left, top, right, bottom}.
     *
     * <p>This is the single answer the overlays consume: the boss bar and Jade
     * both need to clear the same final composition the renderer draws.</p>
     */
    public static int[] occupiedRect(Font font, ClientRunView view,
                                     int screenWidth, int screenHeight) {
        float scale = view.scale();
        String text = view.getFormattedTime();
        int width = (int) (font.width(text) * scale);
        int height = (int) (font.lineHeight * scale);

        com.mateof24.config.TimerPositionPreset preset = view.positionPreset();
        int x, y;
        if (preset == com.mateof24.config.TimerPositionPreset.CUSTOM) {
            x = view.displayX() == -1 ? (screenWidth - width) / 2 : view.displayX();
            y = view.displayY();
        } else {
            x = preset.calculateX(screenWidth, width, view.displayX());
            y = preset.calculateY(screenHeight, height, view.displayY());
            if (x == -1) x = (screenWidth - width) / 2;
        }

        TitleBlock block = of(font, view, x, y, width, height, scale, screenWidth, screenHeight);
        if (block == null) return new int[]{x, y, x + width, y + height};
        TitleLayout.Placement p = block.layout;
        return new int[]{p.left, p.top, p.right, p.bottom};
    }

    /**
     * Union of the occupied rects of every execution matching the filter, or
     * null when none do.
     */
    public static int[] unionRect(Font font, int screenWidth, int screenHeight,
                                  java.util.function.Predicate<ClientRunView> filter) {
        int[] union = null;
        for (ClientRunView view : ClientTimerState.visibleViews()) {
            if (!filter.test(view)) continue;
            int[] rect = occupiedRect(font, view, screenWidth, screenHeight);
            if (union == null) {
                union = rect;
            } else {
                union[0] = Math.min(union[0], rect[0]);
                union[1] = Math.min(union[1], rect[1]);
                union[2] = Math.max(union[2], rect[2]);
                union[3] = Math.max(union[3], rect[3]);
            }
        }
        return union;
    }
}
