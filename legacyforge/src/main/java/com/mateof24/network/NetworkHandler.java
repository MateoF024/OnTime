package com.mateof24.network;

import com.mateof24.OnTimeConstants;
import com.mateof24.config.ModConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(OnTimeConstants.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void registerPackets() {
        CHANNEL.registerMessage(packetId++, TimerSyncPayload.class,
                TimerSyncPayload::encode, TimerSyncPayload::decode, TimerSyncPayload::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(packetId++, TimerVisibilityPayload.class,
                TimerVisibilityPayload::encode, TimerVisibilityPayload::decode, TimerVisibilityPayload::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(packetId++, TimerSilentPayload.class,
                TimerSilentPayload::encode, TimerSilentPayload::decode, TimerSilentPayload::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(packetId++, TimerDisplayConfigPayload.class,
                TimerDisplayConfigPayload::encode, TimerDisplayConfigPayload::decode, TimerDisplayConfigPayload::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void syncVisibilityToClient(ServerPlayer player, boolean visible) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TimerVisibilityPayload(visible));
    }

    public static void syncSilentToClient(ServerPlayer player, boolean silent) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TimerSilentPayload(silent));
    }

    public static void syncTimerToClients(MinecraftServer server, String name,
                                          long currentTicks, long targetTicks,
                                          boolean countUp, boolean running, boolean silent) {
        CHANNEL.send(PacketDistributor.ALL.noArg(),
                new TimerSyncPayload(name, currentTicks, targetTicks, countUp, running, silent, server.getTickCount()));
    }

    public static void syncDisplayConfigToClient(ServerPlayer player, ModConfig cfg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), buildDisplayConfigPayload(cfg));
    }

    public static void syncDisplayConfigToAllClients(MinecraftServer server, ModConfig cfg) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), buildDisplayConfigPayload(cfg));
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