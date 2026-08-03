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

        // The decorative titles (4.0.0) are part of the occupied rect and
        // shift the counter exactly like TitleOverlay does, so Jade is
        // pushed clear of the same final layout.
        int gap = Math.max(1, (int) (com.mateof24.render.TitleLayout.GAP * scale));
        net.minecraft.network.chat.Component[] titles = new net.minecraft.network.chat.Component[4];
        int[] titleWs = new int[4];
        int[] titleHs = new int[4];
        for (int slot = 0; slot < 4; slot++) {
            titles[slot] = ClientTimerState.titleComponent(slot);
            if (titles[slot] == null) continue;
            titleWs[slot] = (int) (mc.font.width(titles[slot]) * scale);
            titleHs[slot] = (int) (mc.font.lineHeight * scale);
        }
        x += com.mateof24.render.TitleLayout.timerShiftX(x, textW, titleWs, gap, screenW);
        y += com.mateof24.render.TitleLayout.timerShiftY(y, textH, titleHs, gap, screenH);

        int left = x;
        int top = y;
        int right = x + textW;
        int bottom = y + textH;

        for (int slot = 0; slot < 4; slot++) {
            if (titles[slot] == null) continue;
            int titleX = com.mateof24.render.TitleLayout.posX(slot, x, textW, titleWs, gap, screenW);
            int titleY = com.mateof24.render.TitleLayout.posY(slot, y, textH, titleHs[slot], gap, screenH);
            left = Math.min(left, titleX);
            top = Math.min(top, titleY);
            right = Math.max(right, titleX + titleWs[slot]);
            bottom = Math.max(bottom, titleY + titleHs[slot]);
        }

        JadeOverlayManager.updateForTimer(left, top, right, bottom, screenW, screenH);
    }

    private static boolean isTopPreset(TimerPositionPreset p) {
        return p == TimerPositionPreset.BOSSBAR
                || p == TimerPositionPreset.TOP_CENTER
                || p == TimerPositionPreset.TOP_LEFT
                || p == TimerPositionPreset.TOP_RIGHT;
    }
}
