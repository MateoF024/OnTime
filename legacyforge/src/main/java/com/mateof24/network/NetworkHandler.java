package com.mateof24.network;

import com.mateof24.OnTimeConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The five messages, over Forge 47's SimpleChannel.
 *
 * <p>The other half of the same protocol Fabric speaks on this branch and both
 * loaders speak on 'main'. What is sent is decided above this class — by
 * {@link TimerState}, which works out who sees which executions — and the
 * shapes are the same fields in the same order. What differs is only the
 * plumbing: Forge registers typed messages on one channel where Fabric names a
 * channel per message.</p>
 *
 * <p>The protocol version is bumped to 2. A 4.0.0 client and a 5.0.0 server
 * now refuse each other at the handshake rather than agreeing to exchange
 * packets neither of them understands.</p>
 */
public class NetworkHandler {

    private static final String PROTOCOL_VERSION = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(OnTimeConstants.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void registerPackets() {
        CHANNEL.registerMessage(packetId++, TimerStatePayload.class,
                TimerStatePayload::encode, TimerStatePayload::decode, TimerStatePayload::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(packetId++, TimerVisibilityPayload.class,
                TimerVisibilityPayload::encode, TimerVisibilityPayload::decode, TimerVisibilityPayload::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(packetId++, TimerSilentPayload.class,
                TimerSilentPayload::encode, TimerSilentPayload::decode, TimerSilentPayload::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(packetId++, AdminStatePayload.class,
                AdminStatePayload::encode, AdminStatePayload::decode, AdminStatePayload::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(packetId++, AdminActionPayload.class,
                AdminActionPayload::encode, AdminActionPayload::decode, AdminActionPayload::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    /**
     * One payload per distinct view: with a single global run that is one
     * payload for the whole server, exactly as cheap as the old broadcast.
     */
    public static void sendTimerState(MinecraftServer server) {
        List<UUID> online = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) online.add(player.getUUID());

        for (var entry : TimerState.groupByView(online).entrySet()) {
            TimerStatePayload payload = new TimerStatePayload(entry.getKey());
            for (UUID id : entry.getValue()) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
            }
        }
    }

    public static void sendTimerState(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new TimerStatePayload(TimerState.viewFor(player.getUUID())));
    }

    public static void syncVisibilityToClient(ServerPlayer player, boolean visible) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TimerVisibilityPayload(visible));
    }

    public static void syncSilentToClient(ServerPlayer player, boolean silent) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TimerSilentPayload(silent));
    }

    public static void sendAdminState(ServerPlayer player, String json) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new AdminStatePayload(json));
    }

    /** Sends one panel action. The server decides whether it is allowed. */
    public static void sendAdminAction(String json) {
        CHANNEL.sendToServer(new AdminActionPayload(json));
    }
}
