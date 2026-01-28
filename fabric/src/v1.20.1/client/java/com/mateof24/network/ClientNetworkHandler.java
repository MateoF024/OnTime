package com.mateof24.network;

import com.mateof24.render.ClientTimerState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;

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
    }
}