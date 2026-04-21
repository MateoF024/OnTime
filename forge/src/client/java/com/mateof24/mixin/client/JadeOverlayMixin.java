package com.mateof24.mixin.client;

import com.mateof24.integration.JadeIntegration;
import com.mateof24.render.ClientTimerState;
import com.mateof24.config.TimerPositionPreset;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "snownee.jade.overlay.OverlayRenderer", remap = false)
public class JadeOverlayMixin {

    private static boolean ontime_shifted = false;

    @Inject(method = "renderOverlay", at = @At("HEAD"), require = 0)
    private static void ontime_onRenderHead(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        ontime_shifted = false;
        if (!ClientTimerState.shouldDisplay()) return;
        TimerPositionPreset preset = ClientTimerState.getPositionPreset();
        if (!isTopPreset(preset)) return;
        Minecraft mc = Minecraft.getInstance();
        int timerH = (int)(mc.font.lineHeight * ClientTimerState.getDisplayScale());
        int offset  = ClientTimerState.getDisplayY() + timerH + 4;
        JadeIntegration.reportRenderedBottom(offset);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, offset, 0.0f);
        ontime_shifted = true;
    }

    @Inject(method = "renderOverlay", at = @At("RETURN"), require = 0)
    private static void ontime_onRenderReturn(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ontime_shifted) {
            graphics.pose().popPose();
            ontime_shifted = false;
        }
    }

    private static boolean isTopPreset(TimerPositionPreset p) {
        return p == TimerPositionPreset.BOSSBAR || p == TimerPositionPreset.TOP_CENTER
                || p == TimerPositionPreset.TOP_LEFT || p == TimerPositionPreset.TOP_RIGHT
                || p == TimerPositionPreset.ACTIONBAR;
    }
}