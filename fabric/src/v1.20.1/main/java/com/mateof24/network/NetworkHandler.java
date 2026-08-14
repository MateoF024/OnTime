package com.mateof24.network;

import com.mateof24.OnTime;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The five channels, over 1.20.1's raw buffers.
 *
 * <p>This is the one part of the port that is written rather than copied.
 * Everything above it is the same file on both branches, including the shapes
 * being sent: {@link TimerState} decides who sees which executions and
 * {@link RunView} is what one of them looks like on the wire. What differs is
 * only how the bytes get there.</p>
 *
 * <p>1.20.1 has no {@code CustomPacketPayload}, no {@code StreamCodec} and no
 * {@code PayloadTypeRegistry} — verified against the mapped jar, the classes
 * are absent. A packet here is an id and a buffer, and the codec is the pair of
 * write/read methods written out by hand. The field order is the one the
 * payload codec on 'main' uses, in the same order, so the two protocols are the
 * same protocol.</p>
 *
 * <p>The channel is named {@code timer_state} rather than 4.0.0's
 * {@code timer_sync}, exactly as on 'main': a 4.x client then never receives it
 * at all, instead of decoding a packet whose shape changed underneath it.</p>
 */
public class NetworkHandler {

    public static final ResourceLocation TIMER_STATE_ID = new ResourceLocation(OnTime.MOD_ID, "timer_state");
    public static final ResourceLocation TIMER_VISIBILITY_ID = new ResourceLocation(OnTime.MOD_ID, "timer_visibility");
    public static final ResourceLocation TIMER_SILENT_ID = new ResourceLocation(OnTime.MOD_ID, "timer_silent");

    /**
     * The admin panel's two channels.
     *
     * <p>{@code admin_action} is the mod's <b>first C2S channel</b>. Everything
     * before this was the server telling clients what to draw; this is a client
     * asking the server to do something, so it is the first packet an attacker
     * can forge. The receiver hands straight to {@link com.mateof24.admin.AdminHandler},
     * which re-checks the permission and every argument — having the screen
     * open proves nothing.</p>
     */
    public static final ResourceLocation ADMIN_STATE_ID = new ResourceLocation(OnTime.MOD_ID, "admin_state");
    public static final ResourceLocation ADMIN_ACTION_ID = new ResourceLocation(OnTime.MOD_ID, "admin_action");

    /**
     * Caps on the two JSON strings. The state one is generous because it
     * carries every timer; the action one is deliberately small — an action is
     * a handful of fields, and a client should not be able to make the server
     * allocate a megabyte by saying it will.
     */
    private static final int MAX_ADMIN_JSON = 1 << 20;
    private static final int MAX_ADMIN_ACTION = 8192;

    public static void registerPackets() {
        ServerPlayNetworking.registerGlobalReceiver(ADMIN_ACTION_ID,
                (server, player, handler, buf, sender) -> {
                    // Read on the netty thread, acted on the server one. The
                    // buffer is not valid past this callback, so the string
                    // comes out here and the rest happens where it is safe.
                    String json = buf.readUtf(MAX_ADMIN_ACTION);
                    server.execute(() -> onAdminAction(server, player, json));
                });
    }

    // ------------------------------------------------------------------
    // Executions
    // ------------------------------------------------------------------

    /**
     * One buffer per recipient, filled by the same writer.
     *
     * <p>A buffer cannot be handed to two sends: netty releases it once it has
     * been written, and the second player would get whatever was left. The
     * payload on 'main' is an immutable record and can be sent to a whole
     * group; here the group shares the <em>writer</em> instead, which costs one
     * buffer per player and nothing else.</p>
     */
    private static void sendTo(ServerPlayer player, ResourceLocation channel, Consumer<FriendlyByteBuf> write) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        write.accept(buf);
        ServerPlayNetworking.send(player, channel, buf);
    }

    /**
     * One view per distinct audience: with a single global run that is one
     * shape for the whole server, exactly as cheap as the old broadcast.
     */
    public static void sendTimerState(MinecraftServer server) {
        List<UUID> online = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) online.add(player.getUUID());

        for (var entry : TimerState.groupByView(online).entrySet()) {
            Consumer<FriendlyByteBuf> write = buf -> writeRuns(buf, entry.getKey());
            for (UUID id : entry.getValue()) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) sendTo(player, TIMER_STATE_ID, write);
            }
        }
    }

    public static void sendTimerState(ServerPlayer player) {
        List<RunView> runs = TimerState.viewFor(player.getUUID());
        sendTo(player, TIMER_STATE_ID, buf -> writeRuns(buf, runs));
    }

    /**
     * The full set of executions a player can see.
     *
     * <p>A snapshot rather than a delta: it is self-healing, and at roughly
     * sixty bytes per run the size never matters.</p>
     */
    public static void writeRuns(FriendlyByteBuf buf, List<RunView> runs) {
        buf.writeVarInt(runs.size());
        for (RunView v : runs) {
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
    }

    /** The other half of {@link #writeRuns}, read in the same order. */
    public static List<RunView> readRuns(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<RunView> runs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            runs.add(new RunView(
                    buf.readUUID(), buf.readUtf(), buf.readLong(), buf.readLong(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                    buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
                    buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(), buf.readFloat(), buf.readFloat()));
        }
        return runs;
    }

    public static void syncVisibilityToClient(ServerPlayer player, boolean visible) {
        sendTo(player, TIMER_VISIBILITY_ID, buf -> buf.writeBoolean(visible));
    }

    public static void syncSilentToClient(ServerPlayer player, boolean silent) {
        sendTo(player, TIMER_SILENT_ID, buf -> buf.writeBoolean(silent));
    }

    // ------------------------------------------------------------------
    // The admin panel
    // ------------------------------------------------------------------

    public static void sendAdminState(ServerPlayer player, String json) {
        sendTo(player, ADMIN_STATE_ID, buf -> buf.writeUtf(json, MAX_ADMIN_JSON));
    }

    /**
     * Handles one action from an open panel.
     *
     * <p>Already on the server thread by the time this runs, and it hands the
     * whole decision to AdminHandler, which authorises the caller and validates
     * the arguments. Nothing is trusted here beyond "this is the player the
     * connection belongs to".</p>
     */
    private static void onAdminAction(MinecraftServer server, ServerPlayer player, String json) {
        com.google.gson.JsonObject request;
        try {
            request = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            return;
        }
        com.mateof24.admin.AdminHandler.handle(server, player, request);
    }
}
