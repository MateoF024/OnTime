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

    @ModifyVariable(method = "render", at = @At(value = "STORE"), ordinal = 1)
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

        int bossBarLeft = (screenWidth - BOSSBAR_WIDTH) / 2;
        int bossBarRight = bossBarLeft + BOSSBAR_WIDTH;

        boolean horizontalOverlap = (timerX + timerWidth) > bossBarLeft && timerX < bossBarRight;
        boolean verticalOverlap = (timerY + timerHeight) > BOSSBAR_DEFAULT_Y && timerY < (BOSSBAR_DEFAULT_Y + BOSSBAR_HEIGHT);

        if (horizontalOverlap && verticalOverlap) {
            return y + (timerY + timerHeight - BOSSBAR_DEFAULT_Y) + 10;
        }

        return y;
    }
}