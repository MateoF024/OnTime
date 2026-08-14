package com.mateof24.network;

import com.mateof24.OnTime;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class NetworkHandler {
    public static final Identifier TIMER_STATE_ID = Identifier.fromNamespaceAndPath(OnTime.MOD_ID, "timer_state");
    public static final Identifier TIMER_VISIBILITY_ID = Identifier.fromNamespaceAndPath(OnTime.MOD_ID, "timer_visibility");
    public static final Identifier TIMER_SILENT_ID = Identifier.fromNamespaceAndPath(OnTime.MOD_ID, "timer_silent");

    public static void registerPackets() {
        PayloadTypeRegistry.playS2C().register(TimerStatePayload.TYPE, TimerStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TimerVisibilityPayload.TYPE, TimerVisibilityPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TimerSilentPayload.TYPE, TimerSilentPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AdminStatePayload.TYPE, AdminStatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AdminActionPayload.TYPE, AdminActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(AdminActionPayload.TYPE,
                (payload, context) -> onAdminAction(payload, context.player(), context.server()));
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
                        buf.writeInt(v.colorHigh()); buf.writeInt(v.colorMid()); buf.writeInt(v.colorLow());
                        buf.writeVarInt(v.thresholdMid()); buf.writeVarInt(v.thresholdLow());
                        buf.writeUtf(v.soundId());
                        buf.writeFloat(v.soundVolume()); buf.writeFloat(v.soundPitch());
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
                                buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
                                buf.readInt(), buf.readInt(), buf.readInt(),
                                buf.readVarInt(), buf.readVarInt(),
                                buf.readUtf(), buf.readFloat(), buf.readFloat()));
                    }
                    return new TimerStatePayload(runs);
                }
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * The admin panel's two channels.
     *
     * <p>{@code admin_action} is the mod's <b>first C2S channel</b>. Everything
     * before this was the server telling clients what to draw; this is a client
     * asking the server to do something, so it is the first packet an attacker
     * can forge. The receiver hands straight to {@link com.mateof24.admin.AdminOps},
     * which re-checks the permission and every argument — having the screen
     * open proves nothing.</p>
     */
    public static final Identifier ADMIN_STATE_ID = Identifier.fromNamespaceAndPath(OnTime.MOD_ID, "admin_state");
    public static final Identifier ADMIN_ACTION_ID = Identifier.fromNamespaceAndPath(OnTime.MOD_ID, "admin_action");

    public record AdminStatePayload(String json) implements CustomPacketPayload {
        public static final Type<AdminStatePayload> TYPE = new Type<>(ADMIN_STATE_ID);
        public static final StreamCodec<FriendlyByteBuf, AdminStatePayload> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.json(), MAX_ADMIN_JSON),
                buf -> new AdminStatePayload(buf.readUtf(MAX_ADMIN_JSON))
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** {@code {"op": "...", "args": {...}}}, validated server-side. */
    public record AdminActionPayload(String json) implements CustomPacketPayload {
        public static final Type<AdminActionPayload> TYPE = new Type<>(ADMIN_ACTION_ID);
        public static final StreamCodec<FriendlyByteBuf, AdminActionPayload> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.json(), MAX_ADMIN_ACTION),
                buf -> new AdminActionPayload(buf.readUtf(MAX_ADMIN_ACTION))
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Caps on the two JSON strings. The state one is generous because it
     * carries every timer; the action one is deliberately small — an action is
     * a handful of fields, and a client should not be able to make the server
     * allocate a megabyte by saying it will.
     */
    private static final int MAX_ADMIN_JSON = 1 << 20;
    private static final int MAX_ADMIN_ACTION = 8192;

    public static void sendAdminState(ServerPlayer player, String json) {
        ServerPlayNetworking.send(player, new AdminStatePayload(json));
    }

    /**
     * Handles one action from an open panel.
     *
     * <p>Hops to the server thread before touching anything — the payload
     * arrives on a netty thread — and hands the whole decision to AdminOps,
     * which authorises the caller and validates the arguments. Nothing is
     * trusted here beyond "this is the player the connection belongs to".</p>
     */
    private static void onAdminAction(AdminActionPayload payload, ServerPlayer player, MinecraftServer server) {
        server.execute(() -> {
            com.google.gson.JsonObject request;
            try {
                request = com.google.gson.JsonParser.parseString(payload.json()).getAsJsonObject();
            } catch (Exception e) {
                return;
            }
            com.mateof24.admin.AdminHandler.handle(server, player, request);
        });
    }
}
