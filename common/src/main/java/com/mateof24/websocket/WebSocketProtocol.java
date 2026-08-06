package com.mateof24.websocket;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RFC 6455, by hand, in the JDK alone.
 *
 * <p>The mod takes no dependencies, so a WebSocket has to be written rather
 * than imported. It is less code than it sounds: a handshake that is one SHA-1
 * of a string the client sent, and a frame format of two header bytes, an
 * occasional longer length, and four bytes of mask. Everything here is
 * {@code MessageDigest}, {@code Base64} and arithmetic.</p>
 *
 * <p>Deliberately free of Minecraft and of the server it serves: this is the
 * wire, and keeping it that way is what lets it be tested against the RFC's own
 * example vectors rather than against a running game.</p>
 */
public final class WebSocketProtocol {

    private WebSocketProtocol() {}

    /** The constant every RFC 6455 handshake mixes into the client's key. */
    static final String MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    /** Frames larger than this are refused rather than allocated. */
    public static final int MAX_PAYLOAD = 1 << 20;

    // ---- opcodes ----

    public static final int OP_CONTINUATION = 0x0;
    public static final int OP_TEXT = 0x1;
    public static final int OP_BINARY = 0x2;
    public static final int OP_CLOSE = 0x8;
    public static final int OP_PING = 0x9;
    public static final int OP_PONG = 0xA;

    // ---- close codes, the few this server ever sends ----

    public static final int CLOSE_NORMAL = 1000;
    public static final int CLOSE_GOING_AWAY = 1001;
    public static final int CLOSE_PROTOCOL_ERROR = 1002;
    public static final int CLOSE_TOO_BIG = 1009;
    /** Not in the RFC's own list; the range from 4000 is reserved for the application. */
    public static final int CLOSE_UNAUTHORISED = 4001;

    /**
     * The value of {@code Sec-WebSocket-Accept} for a client's key.
     *
     * <p>base64(SHA-1(key + magic)), which is the whole of the handshake's
     * cryptography. It proves nothing about who is connecting — it only proves
     * the other end speaks the protocol, so it is not a substitute for the
     * token check.</p>
     */
    public static String acceptKey(String clientKey) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest((clientKey + MAGIC).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 is required of every Java runtime; if it is missing the
            // problem is not one this mod can do anything about.
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }

    /** One request line plus its headers, as read from a socket. */
    public record Handshake(String method, String target, Map<String, String> headers) {

        public String header(String name) {
            return headers.get(name.toLowerCase(java.util.Locale.ROOT));
        }

        /** True when this really is a WebSocket upgrade and not a plain GET. */
        public boolean isUpgrade() {
            String upgrade = header("Upgrade");
            String connection = header("Connection");
            return upgrade != null && upgrade.equalsIgnoreCase("websocket")
                    && connection != null
                    && connection.toLowerCase(java.util.Locale.ROOT).contains("upgrade")
                    && header("Sec-WebSocket-Key") != null;
        }

        /** The query string's value for a parameter, or null. */
        public String query(String name) {
            int mark = target.indexOf('?');
            if (mark < 0) return null;
            for (String pair : target.substring(mark + 1).split("&")) {
                int eq = pair.indexOf('=');
                if (eq < 0) continue;
                if (pair.substring(0, eq).equals(name)) {
                    return java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                }
            }
            return null;
        }
    }

    /**
     * Reads the request line and headers, stopping at the blank line.
     *
     * @param first the bytes already taken off the stream to decide what this
     *              connection is, which have to be put back logically
     * @return null when the stream ends or the request is not readable
     */
    public static Handshake readHandshake(String first, InputStream in) throws IOException {
        String requestLine = first != null ? first : readLine(in);
        if (requestLine == null || requestLine.isEmpty()) return null;

        String[] parts = requestLine.split(" ");
        if (parts.length < 2) return null;

        Map<String, String> headers = new LinkedHashMap<>();
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            headers.put(line.substring(0, colon).trim().toLowerCase(java.util.Locale.ROOT),
                    line.substring(colon + 1).trim());
        }
        return new Handshake(parts[0], parts[1], headers);
    }

    /**
     * One line of an HTTP head.
     *
     * <p>Read a byte at a time on purpose: a {@code BufferedReader} would take
     * more of the stream than the head, and what follows the head is binary
     * frames that must still be there afterwards.</p>
     */
    public static String readLine(InputStream in) throws IOException {
        StringBuilder out = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') return out.toString();
            if (c != '\r') out.append((char) c);
            if (out.length() > 8192) throw new IOException("Header line too long");
        }
        return out.length() == 0 ? null : out.toString();
    }

    /** The 101 response that completes an accepted handshake. */
    public static String upgradeResponse(String clientKey, String protocol) {
        StringBuilder out = new StringBuilder();
        out.append("HTTP/1.1 101 Switching Protocols\r\n");
        out.append("Upgrade: websocket\r\n");
        out.append("Connection: Upgrade\r\n");
        out.append("Sec-WebSocket-Accept: ").append(acceptKey(clientKey)).append("\r\n");
        // Echoed only when the client offered one: answering with a protocol
        // that was never proposed is a handshake failure on the client side.
        if (protocol != null && !protocol.isEmpty()) {
            out.append("Sec-WebSocket-Protocol: ").append(protocol).append("\r\n");
        }
        out.append("\r\n");
        return out.toString();
    }

    /** A refusal that a browser can read, for a handshake that will not be honoured. */
    public static String rejectResponse(int status, String reason) {
        String body = reason == null ? "" : reason;
        return "HTTP/1.1 " + status + " " + (status == 401 ? "Unauthorized" : "Bad Request") + "\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n"
                + "Connection: close\r\n\r\n" + body;
    }

    /** One frame off the wire. */
    public record Frame(boolean fin, int opcode, byte[] payload) {

        public String text() {
            return new String(payload, StandardCharsets.UTF_8);
        }

        /** The close code a CLOSE frame carries, or {@link #CLOSE_NORMAL} when it carries none. */
        public int closeCode() {
            if (opcode != OP_CLOSE || payload.length < 2) return CLOSE_NORMAL;
            return ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
        }
    }

    /**
     * Reads one frame.
     *
     * <p>A frame from a client must be masked — the RFC requires it, and a
     * server that accepts an unmasked one is the hole cache-poisoning attacks
     * were designed around — so an unmasked frame is a protocol error rather
     * than something to be lenient about.</p>
     *
     * @return null at end of stream
     */
    public static Frame readFrame(InputStream in) throws IOException {
        int first = in.read();
        if (first == -1) return null;
        int second = in.read();
        if (second == -1) return null;

        boolean fin = (first & 0x80) != 0;
        if ((first & 0x70) != 0) throw new ProtocolException("Reserved bits set");
        int opcode = first & 0x0F;

        boolean masked = (second & 0x80) != 0;
        if (!masked) throw new ProtocolException("Client frame is not masked");

        long length = second & 0x7F;
        if (length == 126) {
            length = ((long) readByte(in) << 8) | readByte(in);
        } else if (length == 127) {
            length = 0;
            for (int i = 0; i < 8; i++) length = (length << 8) | readByte(in);
        }
        if (length < 0 || length > MAX_PAYLOAD) throw new TooLargeException(length);

        byte[] mask = new byte[4];
        readFully(in, mask);

        byte[] payload = new byte[(int) length];
        readFully(in, payload);
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (payload[i] ^ mask[i & 3]);
        }
        return new Frame(fin, opcode, payload);
    }

    /**
     * Writes one frame, never masked.
     *
     * <p>The RFC forbids a server masking, and unlike the client's obligation
     * this one is easy to get wrong silently: browsers simply drop the
     * connection without saying why.</p>
     */
    public static void writeFrame(OutputStream out, int opcode, byte[] payload) throws IOException {
        int length = payload.length;
        // Assembled and written in one go: two writes for one frame lets
        // another thread's frame land between the header and its body.
        byte[] header = new byte[length < 126 ? 2 : length <= 0xFFFF ? 4 : 10];
        header[0] = (byte) (0x80 | opcode);
        if (length < 126) {
            header[1] = (byte) length;
        } else if (length <= 0xFFFF) {
            header[1] = 126;
            header[2] = (byte) (length >> 8);
            header[3] = (byte) length;
        } else {
            header[1] = 127;
            for (int i = 0; i < 8; i++) header[2 + i] = (byte) (length >>> (8 * (7 - i)));
        }

        byte[] frame = new byte[header.length + length];
        System.arraycopy(header, 0, frame, 0, header.length);
        System.arraycopy(payload, 0, frame, header.length, length);
        synchronized (out) {
            out.write(frame);
            out.flush();
        }
    }

    public static void writeText(OutputStream out, String text) throws IOException {
        writeFrame(out, OP_TEXT, text.getBytes(StandardCharsets.UTF_8));
    }

    /** A close frame carrying a code and, when it helps, a reason. */
    public static void writeClose(OutputStream out, int code, String reason) throws IOException {
        byte[] text = reason == null ? new byte[0] : reason.getBytes(StandardCharsets.UTF_8);
        // The RFC caps a close frame's payload at 125 bytes, two of them the
        // code, so a long reason is trimmed rather than sent illegally.
        if (text.length > 123) {
            byte[] trimmed = new byte[123];
            System.arraycopy(text, 0, trimmed, 0, 123);
            text = trimmed;
        }
        byte[] payload = new byte[2 + text.length];
        payload[0] = (byte) (code >> 8);
        payload[1] = (byte) code;
        System.arraycopy(text, 0, payload, 2, text.length);
        writeFrame(out, OP_CLOSE, payload);
    }

    /** Thrown when the other end is not speaking the protocol. */
    public static class ProtocolException extends IOException {
        public ProtocolException(String message) { super(message); }
    }

    /** Thrown for a frame this server will not allocate memory for. */
    public static class TooLargeException extends IOException {
        private final long length;

        public TooLargeException(long length) {
            super("Frame of " + length + " bytes exceeds the limit of " + MAX_PAYLOAD);
            this.length = length;
        }

        public long length() { return length; }
    }

    private static int readByte(InputStream in) throws IOException {
        int value = in.read();
        if (value == -1) throw new EOFException("Stream ended inside a frame header");
        return value;
    }

    private static void readFully(InputStream in, byte[] buffer) throws IOException {
        int read = 0;
        while (read < buffer.length) {
            int count = in.read(buffer, read, buffer.length - read);
            if (count == -1) throw new EOFException("Stream ended inside a frame");
            read += count;
        }
    }
}
