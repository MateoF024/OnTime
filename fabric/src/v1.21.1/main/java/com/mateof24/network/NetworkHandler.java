package com.mateof24.network;

import com.mateof24.OnTime;
import com.mateof24.config.ModConfig;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class NetworkHandler {
    public static final ResourceLocation TIMER_SYNC_ID = ResourceLocation.fromNamespaceAndPath(OnTime.MOD_ID, "timer_sync");
    public static final ResourceLocation TIMER_VISIBILITY_ID = ResourceLocation.fromNamespaceAndPath(OnTime.MOD_ID, "timer_visibility");
    public static final ResourceLocation TIMER_SILENT_ID = ResourceLocation.fromNamespaceAndPath(OnTime.MOD_ID, "timer_silent");
    public static final ResourceLocation TIMER_DISPLAY_CONFIG_ID = ResourceLocation.fromNamespaceAndPath(OnTime.MOD_ID, "timer_display_config");

    public static void registerPackets() {
        PayloadTypeRegistry.playS2C().register(TimerSyncPayload.TYPE, TimerSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TimerVisibilityPayload.TYPE, TimerVisibilityPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TimerSilentPayload.TYPE, TimerSilentPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TimerDisplayConfigPayload.TYPE, TimerDisplayConfigPayload.CODEC);
    }

    public static void syncTimerToClients(MinecraftServer server, String name, long currentTicks,
                                          long targetTicks, boolean countUp, boolean running, boolean silent) {
        TimerSyncPayload payload = new TimerSyncPayload(name, currentTicks, targetTicks, countUp, running, silent, server.getTickCount());
        for (var player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void syncVisibilityToClient(ServerPlayer player, boolean visible) {
        ServerPlayNetworking.send(player, new TimerVisibilityPayload(visible));
    }

    public static void syncSilentToClient(ServerPlayer player, boolean silent) {
        ServerPlayNetworking.send(player, new TimerSilentPayload(silent));
    }

    public static void syncDisplayConfigToClient(ServerPlayer player, ModConfig cfg) {
        ServerPlayNetworking.send(player, buildDisplayConfigPayload(cfg));
    }

    public static void syncDisplayConfigToAllClients(MinecraftServer server, ModConfig cfg) {
        TimerDisplayConfigPayload payload = buildDisplayConfigPayload(cfg);
        for (var player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static TimerDisplayConfigPayload buildDisplayConfigPayload(ModConfig cfg) {
        return new TimerDisplayConfigPayload(
                cfg.getTimerX(), cfg.getTimerY(), cfg.getPositionPreset().name(), cfg.getTimerScale(),
                cfg.getColorHigh(), cfg.getColorMid(), cfg.getColorLow(),
                cfg.getThresholdMid(), cfg.getThresholdLow(),
                cfg.getTimerSoundId(), cfg.getTimerSoundVolume(), cfg.getTimerSoundPitch()
        );
    }

    public record TimerVisibilityPayload(boolean visible) implements CustomPacketPayload {
        public static final Type<TimerVisibilityPayload> TYPE = new Type<>(TIMER_VISIBILITY_ID);
        public static final StreamCodec<FriendlyByteBuf, TimerVisibilityPayload> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeBoolean(p.visible()),
                buf -> new TimerVisibilityPayload(buf.readBoolean())
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TimerSilentPayload(boolean silent) implements CustomPacketPayload {
        public static final Type<TimerSilentPayload> TYPE = new Type<>(TIMER_SILENT_ID);
        public static final StreamCodec<FriendlyByteBuf, TimerSilentPayload> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeBoolean(p.silent()),
                buf -> new TimerSilentPayload(buf.readBoolean())
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TimerDisplayConfigPayload(
            int timerX, int timerY, String positionPreset, float scale,
            int colorHigh, int colorMid, int colorLow,
            int thresholdMid, int thresholdLow,
            String soundId, float soundVolume, float soundPitch
    ) implements CustomPacketPayload {
        public static final Type<TimerDisplayConfigPayload> TYPE = new Type<>(TIMER_DISPLAY_CONFIG_ID);
        public static final StreamCodec<FriendlyByteBuf, TimerDisplayConfigPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeInt(p.timerX()); buf.writeInt(p.timerY());
                    buf.writeUtf(p.positionPreset()); buf.writeFloat(p.scale());
                    buf.writeInt(p.colorHigh()); buf.writeInt(p.colorMid()); buf.writeInt(p.colorLow());
                    buf.writeInt(p.thresholdMid()); buf.writeInt(p.thresholdLow());
                    buf.writeUtf(p.soundId()); buf.writeFloat(p.soundVolume()); buf.writeFloat(p.soundPitch());
                },
                buf -> new TimerDisplayConfigPayload(
                        buf.readInt(), buf.readInt(), buf.readUtf(), buf.readFloat(),
                        buf.readInt(), buf.readInt(), buf.readInt(),
                        buf.readInt(), buf.readInt(),
                        buf.readUtf(), buf.readFloat(), buf.readFloat()
                )
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TimerSyncPayload(String name, long currentTicks, long targetTicks,
                                   boolean countUp, boolean running, boolean silent, long serverTick)
            implements CustomPacketPayload {
        public static final Type<TimerSyncPayload> TYPE = new Type<>(TIMER_SYNC_ID);
        public static final StreamCodec<FriendlyByteBuf, TimerSyncPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.name()); buf.writeLong(p.currentTicks()); buf.writeLong(p.targetTicks());
                    buf.writeBoolean(p.countUp()); buf.writeBoolean(p.running());
                    buf.writeBoolean(p.silent()); buf.writeLong(p.serverTick());
                },
                buf -> new TimerSyncPayload(
                        buf.readUtf(), buf.readLong(), buf.readLong(),
                        buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readLong()
                )
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}