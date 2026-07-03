package com.mateof24;

import com.mateof24.network.ClientNetworkHandler;
import com.mateof24.render.ClientTimerState;
import com.mateof24.render.TimerRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

public class OnTimeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientNetworkHandler.registerClientPackets();
        // HudRenderCallback was removed in Fabric API 26.1: the HUD is now a
        // registry of named elements. Attached after the boss bar so the timer
        // draws in the same order as the old end-of-frame callback.
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath(OnTime.MOD_ID, "timer_hud"),
                TimerRenderer::render);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientTimerState.tick();
            com.mateof24.integration.JadeClientHook.updateFromTimer();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientTimerState.clear();
            com.mateof24.integration.JadeOverlayManager.resetOnDisconnect();
        });
    }
}
