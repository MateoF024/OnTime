package com.mateof24.network;

import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Sends one admin panel action to the server, which decides whether it is
 * allowed.
 *
 * <p>NeoForge 21.10 and later: the client-to-server send lives on
 * {@code ClientPacketDistributor}. NeoForge moved it out of
 * {@code PacketDistributor} and into a client-only package at 21.10 — one
 * version earlier than the vanilla rework the rest of the compat layer
 * tracks, which is why this file has an axis of its own.</p>
 */
public final class ClientAdminSender {

    private ClientAdminSender() {}

    public static void sendAdminAction(String json) {
        ClientPacketDistributor.sendToServer(new AdminActionPayload(json));
    }
}
