package com.mateof24.mixin.client;

import com.mateof24.render.ClientTimerState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayMixin {

    @Shadow @Final private Minecraft minecraft;
    private static final int BOSSBAR_DEFAULT_Y = 12;
    private static final int BOSSBAR_HEIGHT = 19;
    private static final int BOSSBAR_WIDTH = 182;
    private static final int JADE_ESTIMATED_HEIGHT = 22;

    // 26.1 renamed render(GuiGraphics) to extractRenderState(GuiGraphicsExtractor);
    // the method body (and its local variable layout) is unchanged.
    @ModifyVariable(method = "extractRenderState", at = @At(value = "STORE"), ordinal = 1)
    private int adjustBossBarY(int y) {
        if (!ClientTimerState.shouldDisplay()) return y;

        float scale = ClientTimerState.getDisplayScale();
        int timerX = ClientTimerState.getDisplayX();
        int timerY = ClientTimerState.getDisplayY();

        int screenWidth = this.minecraft.getWindow().getGuiScaledWidth();
        String timeText = ClientTimerState.getFormattedTime();
        int timerWidth = (int) (this.minecraft.font.width(timeText) * scale);
        int timerHeight = (int) (this.minecraft.font.lineHeight * scale);

        if (timerX == -1) timerX = (screenWidth - timerWidth) / 2;

        // Expand to the full occupied rect: the decorative titles (4.0.0)
        // shift the counter and extend the block, and the boss bar must
        // clear ALL of it, not just the counter.
        int screenHeight = this.minecraft.getWindow().getGuiScaledHeight();
        int[] occupied = ClientTimerState.occupiedRectWithTitles(timerX, timerY, timerWidth, timerHeight,
                scale, screenWidth, screenHeight, this.minecraft.font);

        int bossBarLeft = (screenWidth - BOSSBAR_WIDTH) / 2;
        int bossBarRight = bossBarLeft + BOSSBAR_WIDTH;

        boolean horizontalOverlap = occupied[2] > bossBarLeft && occupied[0] < bossBarRight;
        boolean verticalOverlap = occupied[3] > BOSSBAR_DEFAULT_Y && occupied[1] < (BOSSBAR_DEFAULT_Y + BOSSBAR_HEIGHT);

        if (horizontalOverlap && verticalOverlap) {
            int bottomEdge = occupied[3];
            if (com.mateof24.platform.Services.PLATFORM.isModLoaded("jade")) {
                bottomEdge = Math.max(bottomEdge, JADE_ESTIMATED_HEIGHT);
            }
            return y + (bottomEdge - BOSSBAR_DEFAULT_Y) + 10;
        }

        return y;
    }
}
