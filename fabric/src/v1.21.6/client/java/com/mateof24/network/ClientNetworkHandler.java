package com.mateof24.network;

import com.mateof24.render.ClientTimerState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class ClientNetworkHandler {

    public static void registerClientPackets() {
        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.TimerSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (payload.name().isEmpty()) ClientTimerState.clear();
                    else ClientTimerState.updateTimer(payload.name(), payload.currentTicks(), payload.targetTicks(),
                            payload.countUp(), payload.running(), payload.silent(), payload.serverTick());
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.TimerVisibilityPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientTimerState.setVisible(payload.visible()))
        );

        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.TimerSilentPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientTimerState.setPlayerSilent(payload.silent()))
        );

        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.TimerDisplayConfigPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientTimerState.updateDisplayConfig(
                        payload.timerX(), payload.timerY(), payload.positionPreset(), payload.scale(),
                        payload.colorHigh(), payload.colorMid(), payload.colorLow(),
                        payload.thresholdMid(), payload.thresholdLow(),
                        payload.soundId(), payload.soundVolume(), payload.soundPitch()
                ))
        );
    }
}