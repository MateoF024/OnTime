package com.mateof24.network;

import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Sends one admin panel action to the server, which decides whether it is
 * allowed.
 *
 * <p>compat2 (MC 1.21.11+): NeoForge moved the client-to-server send out of {@code PacketDistributor} into {@code net.neoforged.neoforge.client.network.ClientPacketDistributor} —
 * a different package, not just a different class.</p>
 */
public final class ClientAdminSender {

    private ClientAdminSender() {}

    public static void sendAdminAction(String json) {
        ClientPacketDistributor.sendToServer(new AdminActionPayload(json));
    }
}
