package com.mateof24.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                TimerSyncPayload.TYPE,
                TimerSyncPayload.STREAM_CODEC,
                NetworkHandler::handleTimerSync
        );

        registrar.playToClient(
                TimerVisibilityPayload.TYPE,
                TimerVisibilityPayload.STREAM_CODEC,
                NetworkHandler::handleVisibility
        );
    }

    private static void handleTimerSync(TimerSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.name().isEmpty()) {
                ClientTimerState.clear();
            } else {
                ClientTimerState.updateTimer(
                        payload.name(),
                        payload.currentTicks(),
                        payload.targetTicks(),
                        payload.countUp(),
                        payload.running(),
                        payload.silent(),
                        payload.serverTick()
                );
            }
        });
    }

    private static void handleVisibility(TimerVisibilityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientTimerState.setVisible(payload.visible());
        });
    }

    public static void syncTimerToClients(MinecraftServer server, String name,
                                          long currentTicks, long targetTicks,
                                          boolean countUp, boolean running, boolean silent) {
        TimerSyncPayload payload = new TimerSyncPayload(
                name, currentTicks, targetTicks, countUp, running, silent, server.getTickCount()
        );

        PacketDistributor.sendToAllPlayers(payload);
    }

    public static void syncVisibilityToClient(ServerPlayer player, boolean visible) {
        TimerVisibilityPayload payload = new TimerVisibilityPayload(visible);
        PacketDistributor.sendToPlayer(player, payload);
    }
}