package com.mateof24.network;

import com.mateof24.config.ModConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(TimerSyncPayload.TYPE, TimerSyncPayload.STREAM_CODEC, NetworkHandler::handleTimerSync);
        registrar.playToClient(TimerVisibilityPayload.TYPE, TimerVisibilityPayload.STREAM_CODEC, NetworkHandler::handleVisibility);
        registrar.playToClient(TimerSilentPayload.TYPE, TimerSilentPayload.STREAM_CODEC, NetworkHandler::handleSilent);
        registrar.playToClient(TimerDisplayConfigPayload.TYPE, TimerDisplayConfigPayload.STREAM_CODEC, NetworkHandler::handleDisplayConfig);
    }

    private static void handleTimerSync(TimerSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.name().isEmpty()) ClientTimerState.clear();
            else ClientTimerState.updateTimer(payload.name(), payload.currentTicks(), payload.targetTicks(),
                    payload.countUp(), payload.running(), payload.silent(), payload.serverTick());
        });
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

    public static void syncTimerToClients(MinecraftServer server, String name,
                                          long currentTicks, long targetTicks,
                                          boolean countUp, boolean running, boolean silent) {
        PacketDistributor.sendToAllPlayers(new TimerSyncPayload(
                name, currentTicks, targetTicks, countUp, running, silent, server.getTickCount()));
    }

    public static void syncTimerToClient(ServerPlayer player, String name,
                                         long currentTicks, long targetTicks,
                                         boolean countUp, boolean running, boolean silent, long serverTick) {
        PacketDistributor.sendToPlayer(player, new TimerSyncPayload(
                name, currentTicks, targetTicks, countUp, running, silent, serverTick));
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