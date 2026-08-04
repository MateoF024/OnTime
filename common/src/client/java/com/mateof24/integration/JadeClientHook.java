package com.mateof24.integration;

import com.mateof24.render.ClientTimerState;
import net.minecraft.client.Minecraft;

public final class JadeClientHook {

    private JadeClientHook() {}

    public static void updateFromTimer() {
        if (!JadeOverlayManager.isInstalled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.font == null) {
            JadeOverlayManager.restore();
            return;
        }

        if (!ClientTimerState.shouldDisplay()) {
            JadeOverlayManager.restore();
            return;
        }

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        if (screenW <= 0 || screenH <= 0) return;

        // Every visible execution, whatever preset it uses. This used to be
        // filtered to the four presets that sit along the top, which meant a
        // counter on CUSTOM coordinates was invisible to the displacement no
        // matter where the player put it — Jade would draw straight through it.
        // Where a counter is has no bearing on whether Jade overlaps it; that
        // is what the overlap test is for.
        java.util.List<int[]> rects = com.mateof24.render.TitleBlock
                .occupiedRects(mc.font, screenW, screenH);
        if (rects.isEmpty()) {
            JadeOverlayManager.restore();
            return;
        }

        JadeOverlayManager.updateForTimers(rects, screenW, screenH);
    }
}
