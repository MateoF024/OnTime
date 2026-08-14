package com.mateof24.websocket;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Who is allowed in, and how many of them.
 *
 * <p>Split out of the server because it is the part worth reasoning about on
 * its own: it holds the token, counts what is connected and remembers who has
 * been guessing. Free of sockets and of Minecraft, so every rule in it can be
 * checked without opening a port.</p>
 *
 * <p>Until 5.0.0 this channel had no security whatsoever — a plain
 * {@code new ServerSocket(port)} on every interface, and whoever reached it got
 * the server's timers. Read-only, so nothing could be made to happen, but it
 * was still disclosure from off the machine and it contradicted the rule that
 * an administrator controls all of this.</p>
 */
public final class WebSocketGate {

    /** How many clients may be connected at once. */
    public static final int MAX_CLIENTS = 32;

    /** Wrong tokens from one address before it is refused outright. */
    public static final int MAX_FAILURES = 5;

    /** How long a locked-out address stays locked out. */
    public static final long LOCKOUT_MILLIS = 5 * 60_000L;

    /** Addresses remembered at once, so the map cannot be grown without bound. */
    private static final int MAX_TRACKED = 512;

    /** The prefix a browser uses to smuggle the token through a subprotocol. */
    public static final String PROTOCOL_PREFIX = "ontime.token.";

    private volatile String token;
    private int clients = 0;

    /** Address to {failures, when the lockout ends}. */
    private final Map<String, long[]> failures = new LinkedHashMap<>();

    /** Why a connection was turned away, or that it was not. */
    public enum Verdict { ALLOWED, NO_TOKEN, BAD_TOKEN, LOCKED_OUT, TOO_MANY }

    /** A fresh token, as the web panel does it, for the life of one start(). */
    public void open() {
        byte[] bytes = new byte[16];
        new java.security.SecureRandom().nextBytes(bytes);
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        token = out.toString();
        synchronized (this) {
            clients = 0;
            failures.clear();
        }
    }

    /** Invalidates the token: anything still in flight is refused from here on. */
    public void close() {
        token = null;
    }

    public String token() { return token; }

    /**
     * Whether this connection may proceed.
     *
     * <p>The count is only taken when the answer is yes, so a rejected attempt
     * cannot fill the server up by being rejected repeatedly.</p>
     */
    public synchronized Verdict admit(String address, String provided, long now) {
        long[] record = failures.get(address);
        if (record != null && record[1] > now) return Verdict.LOCKED_OUT;

        if (provided == null || provided.isEmpty()) {
            note(address, now);
            return Verdict.NO_TOKEN;
        }
        String expected = token;
        // Constant time, so a wrong token gives nothing away by how long it
        // took to say so.
        boolean ok = expected != null && java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            note(address, now);
            return Verdict.BAD_TOKEN;
        }

        if (clients >= MAX_CLIENTS) return Verdict.TOO_MANY;
        clients++;
        failures.remove(address);
        return Verdict.ALLOWED;
    }

    /** One admitted client has gone. */
    public synchronized void release() {
        if (clients > 0) clients--;
    }

    public synchronized int clientCount() { return clients; }

    private void note(String address, long now) {
        long[] record = failures.computeIfAbsent(address, key -> {
            // Oldest first, so the map stays bounded under a spray of
            // addresses without forgetting whoever is actually knocking.
            if (failures.size() >= MAX_TRACKED) {
                java.util.Iterator<String> it = failures.keySet().iterator();
                if (it.hasNext()) {
                    it.next();
                    it.remove();
                }
            }
            return new long[]{0, 0};
        });
        record[0]++;
        if (record[0] >= MAX_FAILURES) {
            record[1] = now + LOCKOUT_MILLIS;
            record[0] = 0;
        }
    }

    /**
     * The token a handshake is offering, from either place it may carry one.
     *
     * <p>A browser cannot set headers on a {@code WebSocket}, so it has the
     * query string; anything that can set headers has the subprotocol, which
     * keeps the token out of URLs and therefore out of logs.</p>
     */
    public static String tokenOf(WebSocketProtocol.Handshake shake) {
        String protocol = shake.header("Sec-WebSocket-Protocol");
        if (protocol != null) {
            for (String offered : protocol.split(",")) {
                String value = offered.trim();
                if (value.startsWith(PROTOCOL_PREFIX)) {
                    return value.substring(PROTOCOL_PREFIX.length());
                }
            }
        }
        return shake.query("t");
    }
}
