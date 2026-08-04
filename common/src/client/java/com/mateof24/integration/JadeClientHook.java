package com.mateof24.integration;

import com.mateof24.config.TimerPositionPreset;
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

        // Union of every counter sitting at a top preset, titles included:
        // Jade has to clear the whole composition, not one of the counters.
        int[] rect = com.mateof24.render.TitleBlock.unionRect(mc.font, screenW, screenH,
                view -> isTopPreset(view.positionPreset()));
        if (rect == null) {
            JadeOverlayManager.restore();
            return;
        }

        JadeOverlayManager.updateForTimer(rect[0], rect[1], rect[2], rect[3], screenW, screenH);
    }

    private static boolean isTopPreset(TimerPositionPreset p) {
        return p == TimerPositionPreset.BOSSBAR
                || p == TimerPositionPreset.TOP_CENTER
                || p == TimerPositionPreset.TOP_LEFT
                || p == TimerPositionPreset.TOP_RIGHT;
    }
}
