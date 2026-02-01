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

        registrar.playToClient(
                TimerSilentPayload.TYPE,
                TimerSilentPayload.STREAM_CODEC,
                NetworkHandler::handleSilent
        );

        registrar.playToClient(
                TimerPositionPayload.TYPE,
                TimerPositionPayload.STREAM_CODEC,
                NetworkHandler::handlePosition
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

    private static void handleSilent(TimerSilentPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientTimerState.setPlayerSilent(payload.silent());
        });
    }

    private static void handlePosition(TimerPositionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.mateof24.config.ClientConfig config = com.mateof24.config.ClientConfig.getInstance();
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();

            com.mateof24.config.TimerPositionPreset preset =
                    com.mateof24.config.TimerPositionPreset.fromString(payload.presetName());

            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();

            String sampleText = "00:00:00";
            int textWidth = (int) (mc.font.width(sampleText) * config.getTimerScale());
            int textHeight = (int) (mc.font.lineHeight * config.getTimerScale());

            config.applyPreset(preset, screenWidth, screenHeight, textWidth, textHeight);
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

    public static void syncSilentToClient(ServerPlayer player, boolean silent) {
        TimerSilentPayload payload = new TimerSilentPayload(silent);
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void syncPositionToClient(ServerPlayer player, String presetName) {
        TimerPositionPayload payload = new TimerPositionPayload(presetName);
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void syncPositionToAllClients(net.minecraft.server.MinecraftServer server, String presetName) {
        TimerPositionPayload payload = new TimerPositionPayload(presetName);
        PacketDistributor.sendToAllPlayers(payload);
    }
}