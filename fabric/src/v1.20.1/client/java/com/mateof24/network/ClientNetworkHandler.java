package com.mateof24.network;

import com.mateof24.render.ClientTimerState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class ClientNetworkHandler {

    public static void registerClientPackets() {
        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.TIMER_SYNC_ID, (client, handler, buf, responseSender) -> {
            String name = buf.readUtf();
            long currentTicks = buf.readLong();
            long targetTicks = buf.readLong();
            boolean countUp = buf.readBoolean();
            boolean running = buf.readBoolean();
            boolean silent = buf.readBoolean();
            long serverTick = buf.readLong();

            client.execute(() -> {
                if (name.isEmpty()) ClientTimerState.clear();
                else ClientTimerState.updateTimer(name, currentTicks, targetTicks, countUp, running, silent, serverTick);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.TIMER_VISIBILITY_ID, (client, handler, buf, responseSender) -> {
            boolean visible = buf.readBoolean();
            client.execute(() -> ClientTimerState.setVisible(visible));
        });

        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.TIMER_SILENT_ID, (client, handler, buf, responseSender) -> {
            boolean silent = buf.readBoolean();
            client.execute(() -> ClientTimerState.setPlayerSilent(silent));
        });

        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.TIMER_DISPLAY_CONFIG_ID, (client, handler, buf, responseSender) -> {
            int timerX = buf.readInt(); int timerY = buf.readInt();
            String preset = buf.readUtf(); float scale = buf.readFloat();
            int colorHigh = buf.readInt(); int colorMid = buf.readInt(); int colorLow = buf.readInt();
            int thresholdMid = buf.readInt(); int thresholdLow = buf.readInt();
            String soundId = buf.readUtf(); float soundVolume = buf.readFloat(); float soundPitch = buf.readFloat();

            client.execute(() -> ClientTimerState.updateDisplayConfig(
                    timerX, timerY, preset, scale,
                    colorHigh, colorMid, colorLow,
                    thresholdMid, thresholdLow,
                    soundId, soundVolume, soundPitch
            ));
        });
    }
}