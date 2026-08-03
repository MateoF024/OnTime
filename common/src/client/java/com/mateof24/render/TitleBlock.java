package com.mateof24.render;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * The measured, laid-out title block around the counter.
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
     * Measures and lays out the current timer's titles.
     *
     * @return null when the timer has no titles at all, in which case callers
     *         keep the counter rect they already had
     */
    public static TitleBlock of(Font font, int timerX, int timerY,
                                int timerWidth, int timerHeight, float scale,
                                int screenWidth, int screenHeight) {
        if (!ClientTimerState.hasTitles()) return null;

        int gap = Math.max(1, (int) (TitleLayout.GAP * scale));
        Component[] titles = new Component[4];
        int[] widths = new int[4];
        int[] heights = new int[4];
        for (int slot = 0; slot < 4; slot++) {
            titles[slot] = ClientTimerState.titleComponent(slot);
            if (titles[slot] == null) continue;
            widths[slot] = (int) (font.width(titles[slot]) * scale);
            heights[slot] = (int) (font.lineHeight * scale);
        }

        return new TitleBlock(titles, TitleLayout.resolve(timerX, timerY, timerWidth, timerHeight,
                widths, heights, gap, screenWidth, screenHeight));
    }
}
