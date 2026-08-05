package com.mateof24.network;

import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends one admin panel action to the server, which decides whether it is
 * allowed.
 *
 * <p>compat1 (MC 1.21.1-1.21.10): the client send lives on {@code PacketDistributor}, which is server-side only from 1.21.11 on.</p>
 */
public final class ClientAdminSender {

    private ClientAdminSender() {}

    public static void sendAdminAction(String json) {
        PacketDistributor.sendToServer(new AdminActionPayload(json));
    }
}
