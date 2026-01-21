package com.mateof24.mixin.client;

import com.mateof24.config.ClientConfig;
import com.mateof24.network.ClientTimerState;
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

    @ModifyVariable(
            method = "render",
            at = @At(value = "STORE"),
            ordinal = 1
    )
    private int adjustBossBarY(int y) {
        if (!ClientTimerState.shouldDisplay()) {
            return y;
        }

        ClientConfig config = ClientConfig.getInstance();
        int timerY = config.getTimerY();
        int timerX = config.getTimerX();
        float scale = config.getTimerScale();

        int screenWidth = this.minecraft.getWindow().getGuiScaledWidth();
        String timeText = ClientTimerState.getFormattedTime();
        int timerWidth = (int) (this.minecraft.font.width(timeText) * scale);
        int timerHeight = (int) (this.minecraft.font.lineHeight * scale);

        if (timerX == -1) {
            timerX = (screenWidth - timerWidth) / 2;
        }

        int timerLeft = timerX;
        int timerRight = timerX + timerWidth;
        int timerTop = timerY;
        int timerBottom = timerY + timerHeight;

        int bossBarLeft = (screenWidth - BOSSBAR_WIDTH) / 2;
        int bossBarRight = bossBarLeft + BOSSBAR_WIDTH;
        int bossBarTop = BOSSBAR_DEFAULT_Y;
        int bossBarBottom = bossBarTop + BOSSBAR_HEIGHT;

        boolean horizontalOverlap = timerRight > bossBarLeft && timerLeft < bossBarRight;
        boolean verticalOverlap = timerBottom > bossBarTop && timerTop < bossBarBottom;

        if (horizontalOverlap && verticalOverlap) {
            int overlap = timerBottom - bossBarTop;
            return y + overlap + 10;
        }

        return y;
    }
}