package com.mateof24.network;

import com.mateof24.render.ClientTimerState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;

/**
 * The client half of the four channels it listens on.
 *
 * <p>The same four the payload version registers on 'main', doing the same
 * four things. 1.20.1 hands the receiver a raw buffer instead of a decoded
 * payload, so the reading happens here — on the netty thread, before hopping
 * to the client one, because the buffer is not valid past the callback.</p>
 */
public class ClientNetworkHandler {

    public static void registerClientPackets() {
        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.TIMER_STATE_ID,
                (client, handler, buf, sender) -> {
                    var runs = NetworkHandler.readRuns(buf);
                    client.execute(() -> ClientTimerState.applyState(runs));
                });

        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.TIMER_VISIBILITY_ID,
                (client, handler, buf, sender) -> {
                    boolean visible = buf.readBoolean();
                    client.execute(() -> ClientTimerState.setVisible(visible));
                });

        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.TIMER_SILENT_ID,
                (client, handler, buf, sender) -> {
                    boolean silent = buf.readBoolean();
                    client.execute(() -> ClientTimerState.setPlayerSilent(silent));
                });

        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.ADMIN_STATE_ID,
                (client, handler, buf, sender) -> {
                    String json = buf.readUtf(1 << 20);
                    client.execute(() -> com.mateof24.gui.AdminClientState.accept(json));
                });
    }

    /** Sends one panel action. The server decides whether it is allowed. */
    public static void sendAdminAction(String json) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(json, 8192);
        ClientPlayNetworking.send(NetworkHandler.ADMIN_ACTION_ID, buf);
    }
}
