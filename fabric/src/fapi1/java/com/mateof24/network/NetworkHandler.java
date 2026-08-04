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
    public static final ResourceLocation TIMER_STATE_ID = ResourceLocation.fromNamespaceAndPath(OnTime.MOD_ID, "timer_state");
    public static final ResourceLocation TIMER_VISIBILITY_ID = ResourceLocation.fromNamespaceAndPath(OnTime.MOD_ID, "timer_visibility");
    public static final ResourceLocation TIMER_SILENT_ID = ResourceLocation.fromNamespaceAndPath(OnTime.MOD_ID, "timer_silent");
    public static final ResourceLocation TIMER_DISPLAY_CONFIG_ID = ResourceLocation.fromNamespaceAndPath(OnTime.MOD_ID, "timer_display_config");

    public static void registerPackets() {
        PayloadTypeRegistry.playS2C().register(TimerStatePayload.TYPE, TimerStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TimerVisibilityPayload.TYPE, TimerVisibilityPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TimerSilentPayload.TYPE, TimerSilentPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TimerDisplayConfigPayload.TYPE, TimerDisplayConfigPayload.CODEC);
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
                if (player != null) ServerPlayNetworking.send(player, payload);
            }
        }
    }

    public static void sendTimerState(ServerPlayer player) {
        ServerPlayNetworking.send(player,
                TimerStatePayload.of(com.mateof24.network.TimerState.viewFor(player.getUUID())));
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

    /**
     * The full set of executions a player can see.
     *
     * <p>A snapshot rather than a delta: it is self-healing, and at roughly
     * sixty bytes per run the size never matters. The channel is renamed from
     * the 4.0.0 one on purpose — a 4.x client then simply never receives it,
     * instead of decoding a payload whose shape changed underneath it.</p>
     */
    public record TimerStatePayload(java.util.List<com.mateof24.network.RunView> runs)
            implements CustomPacketPayload {
        public static final Type<TimerStatePayload> TYPE = new Type<>(TIMER_STATE_ID);

        public static TimerStatePayload of(java.util.List<com.mateof24.network.RunView> runs) {
            return new TimerStatePayload(runs);
        }

        public static final StreamCodec<FriendlyByteBuf, TimerStatePayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.runs().size());
                    for (com.mateof24.network.RunView v : p.runs()) {
                        buf.writeUUID(v.runId());
                        buf.writeUtf(v.timerName());
                        buf.writeLong(v.currentTicks());
                        buf.writeLong(v.targetTicks());
                        buf.writeBoolean(v.countUp());
                        buf.writeBoolean(v.running());
                        buf.writeBoolean(v.silent());
                        buf.writeUtf(v.titleAbove()); buf.writeUtf(v.titleBelow());
                        buf.writeUtf(v.titleLeft()); buf.writeUtf(v.titleRight());
                        buf.writeUtf(v.preset());
                        buf.writeVarInt(v.x()); buf.writeVarInt(v.y());
                        buf.writeFloat(v.scale());
                    }
                },
                buf -> {
                    int count = buf.readVarInt();
                    java.util.List<com.mateof24.network.RunView> runs = new java.util.ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        runs.add(new com.mateof24.network.RunView(
                                buf.readUUID(), buf.readUtf(), buf.readLong(), buf.readLong(),
                                buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                                buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                                buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readFloat()));
                    }
                    return new TimerStatePayload(runs);
                }
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
