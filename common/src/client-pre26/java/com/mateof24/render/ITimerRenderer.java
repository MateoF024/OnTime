package com.mateof24.render;

import com.mateof24.api.TimerRunInfo;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Hook for replacing how the counter itself is drawn.
 *
 * <p>Called <b>once per execution</b> — a timer can be on screen several times
 * at once, for different audiences and at different times, so the renderer is
 * handed the specific execution it is drawing.</p>
 *
 * <p>Implement exactly one of the two methods. The 4.x signature still works
 * and receives every execution in turn; it simply cannot tell them apart, which
 * is why the new one exists.</p>
 */
public interface ITimerRenderer {

    /**
     * Draws one execution at the position the mod resolved for it.
     *
     * @param run the execution being drawn: its clock, audience, mode and owner
     */
    default void render(GuiGraphics graphics, float partialTick, TimerRunInfo run,
                        int x, int y, float scale) {
        render(graphics, partialTick, run.formattedTime(), run.percentage(), x, y, scale);
    }

    /**
     * @deprecated implement
     *             {@link #render(GuiGraphics, float, TimerRunInfo, int, int, float)}
     *             instead; it says which execution is being drawn.
     */
    @Deprecated
    default void render(GuiGraphics graphics, float partialTick,
                        String formattedTime, float percentage, int x, int y, float scale) {
        throw new UnsupportedOperationException(
                "ITimerRenderer: implement one of the two render methods");
    }
}
