package com.mateof24.network;

import com.mateof24.OnTime;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public class NetworkHandler {
    public static final ResourceLocation TIMER_SYNC_ID = ResourceLocation.fromNamespaceAndPath(OnTime.MOD_ID, "timer_sync");
    public static final ResourceLocation TIMER_VISIBILITY_ID = ResourceLocation.fromNamespaceAndPath(OnTime.MOD_ID, "timer_visibility");
    public static final ResourceLocation TIMER_SILENT_ID = ResourceLocation.fromNamespaceAndPath(OnTime.MOD_ID, "timer_silent");
    public static final ResourceLocation TIMER_POSITION_ID = ResourceLocation.fromNamespaceAndPath(OnTime.MOD_ID, "timer_position");

    public static void registerPackets() {
        PayloadTypeRegistry.playS2C().register(TimerSyncPayload.TYPE, TimerSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TimerVisibilityPayload.TYPE, TimerVisibilityPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TimerSilentPayload.TYPE, TimerSilentPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TimerPositionPayload.TYPE, TimerPositionPayload.CODEC);
    }

    public static void syncVisibilityToClient(net.minecraft.server.level.ServerPlayer player, boolean visible) {
        TimerVisibilityPayload payload = new TimerVisibilityPayload(visible);
        ServerPlayNetworking.send(player, payload);
    }

    public static void syncSilentToClient(net.minecraft.server.level.ServerPlayer player, boolean silent) {
        TimerSilentPayload payload = new TimerSilentPayload(silent);
        ServerPlayNetworking.send(player, payload);
    }

    public static void syncPositionToClient(net.minecraft.server.level.ServerPlayer player, String presetName) {
        TimerPositionPayload payload = new TimerPositionPayload(presetName);
        ServerPlayNetworking.send(player, payload);
    }

    public static void syncPositionToAllClients(net.minecraft.server.MinecraftServer server, String presetName) {
        TimerPositionPayload payload = new TimerPositionPayload(presetName);
        for (var player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public record TimerVisibilityPayload(boolean visible) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TimerVisibilityPayload> TYPE =
                new CustomPacketPayload.Type<>(TIMER_VISIBILITY_ID);

        public static final StreamCodec<FriendlyByteBuf, TimerVisibilityPayload> CODEC = StreamCodec.of(
                TimerVisibilityPayload::write,
                TimerVisibilityPayload::read
        );

        public static void write(FriendlyByteBuf buf, TimerVisibilityPayload payload) {
            buf.writeBoolean(payload.visible());
        }

        public static TimerVisibilityPayload read(FriendlyByteBuf buf) {
            boolean visible = buf.readBoolean();
            return new TimerVisibilityPayload(visible);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record TimerSilentPayload(boolean silent) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TimerSilentPayload> TYPE =
                new CustomPacketPayload.Type<>(TIMER_SILENT_ID);

        public static final StreamCodec<FriendlyByteBuf, TimerSilentPayload> CODEC = StreamCodec.of(
                TimerSilentPayload::write,
                TimerSilentPayload::read
        );

        public static void write(FriendlyByteBuf buf, TimerSilentPayload payload) {
            buf.writeBoolean(payload.silent());
        }

        public static TimerSilentPayload read(FriendlyByteBuf buf) {
            boolean silent = buf.readBoolean();
            return new TimerSilentPayload(silent);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record TimerPositionPayload(String presetName) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TimerPositionPayload> TYPE =
                new CustomPacketPayload.Type<>(TIMER_POSITION_ID);

        public static final StreamCodec<FriendlyByteBuf, TimerPositionPayload> CODEC = StreamCodec.of(
                TimerPositionPayload::write,
                TimerPositionPayload::read
        );

        public static void write(FriendlyByteBuf buf, TimerPositionPayload payload) {
            buf.writeUtf(payload.presetName());
        }

        public static TimerPositionPayload read(FriendlyByteBuf buf) {
            String presetName = buf.readUtf();
            return new TimerPositionPayload(presetName);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void syncTimerToClients(net.minecraft.server.MinecraftServer server, String name,
                                          long currentTicks, long targetTicks, boolean countUp, boolean running, boolean silent) {
        TimerSyncPayload payload = new TimerSyncPayload(name, currentTicks, targetTicks, countUp, running, silent, server.getTickCount());

        for (var player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public record TimerSyncPayload(String name, long currentTicks, long targetTicks, boolean countUp, boolean running, boolean silent, long serverTick)
            implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<TimerSyncPayload> TYPE =
                new CustomPacketPayload.Type<>(TIMER_SYNC_ID);

        public static final StreamCodec<FriendlyByteBuf, TimerSyncPayload> CODEC = StreamCodec.of(
                TimerSyncPayload::write,
                TimerSyncPayload::read
        );

        public static void write(FriendlyByteBuf buf, TimerSyncPayload payload) {
            buf.writeUtf(payload.name());
            buf.writeLong(payload.currentTicks());
            buf.writeLong(payload.targetTicks());
            buf.writeBoolean(payload.countUp());
            buf.writeBoolean(payload.running());
            buf.writeBoolean(payload.silent());
            buf.writeLong(payload.serverTick());
        }

        public static TimerSyncPayload read(FriendlyByteBuf buf) {
            String name = buf.readUtf();
            long currentTicks = buf.readLong();
            long targetTicks = buf.readLong();
            boolean countUp = buf.readBoolean();
            boolean running = buf.readBoolean();
            boolean silent = buf.readBoolean();
            long serverTick = buf.readLong();

            return new TimerSyncPayload(name, currentTicks, targetTicks, countUp, running, silent, serverTick);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}