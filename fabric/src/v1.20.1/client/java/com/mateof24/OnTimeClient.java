package com.mateof24;

import com.mateof24.network.ClientNetworkHandler;
import com.mateof24.render.ClientTimerState;
import com.mateof24.render.TimerRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * The same client entry point 'main' has, minus nothing: every call in it
 * exists in 1.20.1's Fabric API under the same name.
 */
public class OnTimeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientNetworkHandler.registerClientPackets();
        // The panel's two ends: the screen registers how to open itself, the
        // loader registers how to send an action.
        com.mateof24.gui.AdminScreen.register();
        com.mateof24.gui.AdminClientState.setSender(ClientNetworkHandler::sendAdminAction);
        HudRenderCallback.EVENT.register(TimerRenderer::render);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientTimerState.tick();
            com.mateof24.integration.JadeClientHook.updateFromTimer();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientTimerState.clear();
            // Server state does not outlive the connection.
            com.mateof24.gui.AdminClientState.clear();
            com.mateof24.integration.JadeOverlayManager.resetOnDisconnect();
        });
    }
}
