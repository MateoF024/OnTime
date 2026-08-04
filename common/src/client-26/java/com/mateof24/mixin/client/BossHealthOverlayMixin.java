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

        int screenWidth = this.minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = this.minecraft.getWindow().getGuiScaledHeight();

        // Union of every counter that could reach the boss bar, titles
        // included: the bar has to clear the whole block, not one counter.
        int[] occupied = com.mateof24.render.TitleBlock.unionRect(
                this.minecraft.font, screenWidth, screenHeight, view -> true);
        if (occupied == null) return y;

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
