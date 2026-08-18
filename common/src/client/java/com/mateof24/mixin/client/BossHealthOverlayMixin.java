package com.mateof24.mixin.client;

import com.mateof24.render.ClientTimerState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayMixin {

    private static final int BOSSBAR_DEFAULT_Y = 12;
    private static final int BOSSBAR_HEIGHT = 19;
    private static final int BOSSBAR_WIDTH = 182;
    private static final int JADE_ESTIMATED_HEIGHT = 22;

    @ModifyVariable(method = "render", at = @At(value = "STORE"), ordinal = 1)
    private int adjustBossBarY(int y) {
        if (!ClientTimerState.shouldDisplay()) return y;

        // The target's own 'minecraft' field would do, but reaching it needs a
        // @Shadow, and a shadowed field has to be renamed to match whatever
        // names the game is running under. Forge 1.20.1 runs on SRG names and
        // the renaming never happened, so the field was looked up as
        // 'minecraft', not found, and the game died before the main menu. The
        // singleton is the same object and needs no such translation.
        Minecraft minecraft = Minecraft.getInstance();

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        int bossBarLeft = (screenWidth - BOSSBAR_WIDTH) / 2;
        int bossBarRight = bossBarLeft + BOSSBAR_WIDTH;
        int bossBarBottom = BOSSBAR_DEFAULT_Y + BOSSBAR_HEIGHT;

        // Each counter on its own, not their bounding box. A counter pinned to
        // the bottom of the screen and one in the boss bar have a union that
        // covers nearly everything, and pushing the bar clear of that would
        // send it halfway down the screen to avoid empty space.
        int bottomEdge = Integer.MIN_VALUE;
        for (int[] occupied : com.mateof24.render.TitleBlock.occupiedRects(
                minecraft.font, screenWidth, screenHeight)) {
            boolean horizontalOverlap = occupied[2] > bossBarLeft && occupied[0] < bossBarRight;
            boolean verticalOverlap = occupied[3] > BOSSBAR_DEFAULT_Y && occupied[1] < bossBarBottom;
            if (horizontalOverlap && verticalOverlap) {
                bottomEdge = Math.max(bottomEdge, occupied[3]);
            }
        }
        if (bottomEdge == Integer.MIN_VALUE) return y;

        if (com.mateof24.platform.Services.PLATFORM.isModLoaded("jade")) {
            bottomEdge = Math.max(bottomEdge, JADE_ESTIMATED_HEIGHT);
        }
        return y + (bottomEdge - BOSSBAR_DEFAULT_Y) + 10;
    }
}
