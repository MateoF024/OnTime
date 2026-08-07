package com.mateof24.render;

import com.mateof24.api.TimerRunInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Hook for replacing how the counter itself is drawn.
 *
 * <p>26.x variant: Minecraft 26.1 removed {@code GuiGraphics}; HUD drawing goes
 * through {@link GuiGraphicsExtractor}. Custom renderers targeting 26.x must be
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
     * <p>One method. It used to be two, with this one defaulting to an older
     * signature that took a formatted string and a percentage — everything a
     * renderer could want was already on the execution, so the pair only meant
     * two ways to say the same thing.</p>
     *
     * @param run the execution being drawn: its clock, audience, mode and owner
     */
    void render(GuiGraphicsExtractor graphics, float partialTick, TimerRunInfo run,
                int x, int y, float scale);
}
