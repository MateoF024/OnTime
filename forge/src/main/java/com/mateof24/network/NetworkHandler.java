package com.mateof24.network;

import com.mateof24.config.ModConfig;
import com.mateof24.render.ClientTimerState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(TimerStatePayload.TYPE, TimerStatePayload.STREAM_CODEC, NetworkHandler::handleTimerState);
        registrar.playToClient(TimerVisibilityPayload.TYPE, TimerVisibilityPayload.STREAM_CODEC, NetworkHandler::handleVisibility);
        registrar.playToClient(TimerSilentPayload.TYPE, TimerSilentPayload.STREAM_CODEC, NetworkHandler::handleSilent);
        registrar.playToClient(TimerDisplayConfigPayload.TYPE, TimerDisplayConfigPayload.STREAM_CODEC, NetworkHandler::handleDisplayConfig);
    }

    private static void handleTimerState(TimerStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientTimerState.applyState(payload.runs()));
    }

    private static void handleVisibility(TimerVisibilityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientTimerState.setVisible(payload.visible()));
    }

    private static void handleSilent(TimerSilentPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientTimerState.setPlayerSilent(payload.silent()));
    }

    private static void handleDisplayConfig(TimerDisplayConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientTimerState.updateDisplayConfig(
                payload.timerX(), payload.timerY(), payload.positionPreset(), payload.scale(),
                payload.colorHigh(), payload.colorMid(), payload.colorLow(),
                payload.thresholdMid(), payload.thresholdLow(),
                payload.soundId(), payload.soundVolume(), payload.soundPitch()
        ));
    }

    /**
     * One payload per distinct view: with a single global run that is one
     * payload for the whole server, exactly as cheap as the old broadcast.
     */
    public static void sendTimerState(MinecraftServer server) {
        java.util.List<java.util.UUID> online = new java.util.ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) online.add(player.getUUID());

        for (var entry : com.mateof24.network.TimerState.groupByView(online).entrySet()) {
            TimerStatePayload payload = TimerStatePayload.of(entry.getKey());
            for (java.util.UUID id : entry.getValue()) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    public static void sendTimerState(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                TimerStatePayload.of(com.mateof24.network.TimerState.viewFor(player.getUUID())));
    }

    public static void syncVisibilityToClient(ServerPlayer player, boolean visible) {
        PacketDistributor.sendToPlayer(player, new TimerVisibilityPayload(visible));
    }

    public static void syncSilentToClient(ServerPlayer player, boolean silent) {
        PacketDistributor.sendToPlayer(player, new TimerSilentPayload(silent));
    }

    public static void syncDisplayConfigToClient(ServerPlayer player, ModConfig cfg) {
        PacketDistributor.sendToPlayer(player, buildDisplayConfigPayload(cfg));
    }

    public static void syncDisplayConfigToAllClients(MinecraftServer server, ModConfig cfg) {
        PacketDistributor.sendToAllPlayers(buildDisplayConfigPayload(cfg));
    }

    private static TimerDisplayConfigPayload buildDisplayConfigPayload(ModConfig cfg) {
        return new TimerDisplayConfigPayload(
                cfg.getTimerX(), cfg.getTimerY(), cfg.getPositionPreset().name(), cfg.getTimerScale(),
                cfg.getColorHigh(), cfg.getColorMid(), cfg.getColorLow(),
                cfg.getThresholdMid(), cfg.getThresholdLow(),
                cfg.getTimerSoundId(), cfg.getTimerSoundVolume(), cfg.getTimerSoundPitch()
        );
    }
}