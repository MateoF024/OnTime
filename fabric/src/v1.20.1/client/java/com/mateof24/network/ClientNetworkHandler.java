package com.mateof24.network;

import com.mateof24.render.ClientTimerState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class ClientNetworkHandler {

    public static void registerClientPackets() {
        // Registro de paquete de sincronización de timer
        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.TIMER_SYNC_ID, (client, handler, buf, responseSender) -> {
            // Leer datos del buffer
            String name = buf.readUtf();
            long currentTicks = buf.readLong();
            long targetTicks = buf.readLong();
            boolean countUp = buf.readBoolean();
            boolean running = buf.readBoolean();
            boolean silent = buf.readBoolean();
            long serverTick = buf.readLong();

            // Ejecutar en el hilo principal del cliente
            client.execute(() -> {
                if (name.isEmpty()) {
                    ClientTimerState.clear();
                } else {
                    ClientTimerState.updateTimer(name, currentTicks, targetTicks, countUp, running, silent, serverTick);
                }
            });
        });

        // Registro de paquete de visibilidad
        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.TIMER_VISIBILITY_ID, (client, handler, buf, responseSender) -> {
            boolean visible = buf.readBoolean();

            client.execute(() -> {
                ClientTimerState.setVisible(visible);
            });
        });

        // Registro de paquete de silent
        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.TIMER_SILENT_ID, (client, handler, buf, responseSender) -> {
            boolean silent = buf.readBoolean();

            client.execute(() -> {
                ClientTimerState.setPlayerSilent(silent);
            });
        });

        // Registro de paquete de posición
        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.TIMER_POSITION_ID, (client, handler, buf, responseSender) -> {
            String presetName = buf.readUtf();

            client.execute(() -> {
                com.mateof24.config.ClientConfig config = com.mateof24.config.ClientConfig.getInstance();

                com.mateof24.config.TimerPositionPreset preset =
                        com.mateof24.config.TimerPositionPreset.fromString(presetName);

                int screenWidth = client.getWindow().getGuiScaledWidth();
                int screenHeight = client.getWindow().getGuiScaledHeight();

                // Calcular tamaño aproximado del timer
                String sampleText = "00:00:00";
                int textWidth = (int) (client.font.width(sampleText) * config.getTimerScale());
                int textHeight = (int) (client.font.lineHeight * config.getTimerScale());

                config.applyPreset(preset, screenWidth, screenHeight, textWidth, textHeight);
            });
        });

        // Registro de paquete de sonido
        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.TIMER_SOUND_ID, (client, handler, buf, responseSender) -> {
            String soundId = buf.readUtf();
            float volume = buf.readFloat();
            float pitch = buf.readFloat();

            client.execute(() -> {
                ClientTimerState.updateSound(soundId, volume, pitch);
            });
        });
    }
}