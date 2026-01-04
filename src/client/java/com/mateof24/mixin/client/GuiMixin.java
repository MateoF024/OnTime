package com.mateof24.mixin.client;

import com.mateof24.client.ClientTimerManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void renderTimer(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        String displayTime = ClientTimerManager.getDisplayTime();
        if (displayTime == null) return;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();

        int textWidth = mc.font.width(displayTime);
        int x = (screenWidth - textWidth) / 2;
        int y = 8;

        graphics.drawString(mc.font, displayTime, x, y, 0xFFFFFF, true);
    }
}