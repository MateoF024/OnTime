package com.mateof24;

import com.mateof24.network.ClientNetworkHandler;
import com.mateof24.render.ClientTimerState;
import com.mateof24.render.TimerRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class OnTimeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientNetworkHandler.registerClientPackets();
        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> TimerRenderer.render(guiGraphics, tickDelta));
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