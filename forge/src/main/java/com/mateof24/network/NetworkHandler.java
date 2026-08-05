package com.mateof24.network;

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
        registrar.playToClient(AdminStatePayload.TYPE, AdminStatePayload.STREAM_CODEC, NetworkHandler::handleAdminState);
        registrar.playToServer(AdminActionPayload.TYPE, AdminActionPayload.STREAM_CODEC, NetworkHandler::handleAdminAction);
    }

    private static void handleAdminState(AdminStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.mateof24.gui.AdminClientState.accept(payload.json()));
    }

    /**
     * The mod's only server-bound handler.
     *
     * <p>Hops to the server thread and hands the whole decision to AdminOps,
     * which authorises the caller and validates every argument. Nothing is
     * trusted here beyond "this is the player the connection belongs to".</p>
     */
    private static void handleAdminAction(AdminActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        // enqueueWork is the hop to the server thread; IPayloadContext carries
        // no server, and the source stack is the version-stable way to it.
        context.enqueueWork(() -> {
            MinecraftServer server = player.createCommandSourceStack().getServer();
            if (server == null) return;
            com.google.gson.JsonObject request;
            try {
                request = com.google.gson.JsonParser.parseString(payload.json()).getAsJsonObject();
            } catch (Exception e) {
                return;
            }
            com.mateof24.admin.AdminHandler.handle(server, player, request);
        });
    }

    public static void sendAdminState(ServerPlayer player, String json) {
        PacketDistributor.sendToPlayer(player, new AdminStatePayload(json));
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

}