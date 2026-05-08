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

        TimerPositionPreset preset = ClientTimerState.getPositionPreset();
        if (!isTopPreset(preset)) {
            JadeOverlayManager.restore();
            return;
        }

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        if (screenW <= 0 || screenH <= 0) return;

        String text = ClientTimerState.getFormattedTime();
        float scale = ClientTimerState.getDisplayScale();
        int textW = (int) (mc.font.width(text) * scale);
        int textH = (int) (mc.font.lineHeight * scale);

        int x = preset.calculateX(screenW, textW, ClientTimerState.getDisplayX());
        int y = preset.calculateY(screenH, textH, ClientTimerState.getDisplayY());
        if (x == -1) x = (screenW - textW) / 2;

        int left = x;
        int top = y;
        int right = x + textW;
        int bottom = y + textH;

        JadeOverlayManager.updateForTimer(left, top, right, bottom, screenW, screenH);
    }

    private static boolean isTopPreset(TimerPositionPreset p) {
        return p == TimerPositionPreset.BOSSBAR
                || p == TimerPositionPreset.TOP_CENTER
                || p == TimerPositionPreset.TOP_LEFT
                || p == TimerPositionPreset.TOP_RIGHT;
    }
}
