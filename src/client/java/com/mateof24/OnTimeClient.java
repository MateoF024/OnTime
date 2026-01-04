package com.mateof24;

import com.mateof24.network.NetworkHandler;
import com.mateof24.render.TimerRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class OnTimeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        NetworkHandler.registerClientPackets();

        HudRenderCallback.EVENT.register(TimerRenderer::render);
    }
}