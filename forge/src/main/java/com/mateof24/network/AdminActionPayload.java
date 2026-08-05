package com.mateof24.network;

import com.mateof24.OnTimeConstants;
import com.mateof24.compat.VanillaCompat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * One admin panel action: {@code {"op": "...", "args": {...}}}.
 *
 * <p>The mod's first client-to-server payload. Everything before it was the
 * server telling clients what to draw; this is a client asking the server to
 * act, so it is the first packet anyone can forge. The cap is deliberately
 * small — an action is a handful of fields, and a client should not be able to
 * make the server allocate a megabyte by claiming it will.</p>
 */
public record AdminActionPayload(String json) implements CustomPacketPayload {

    private static final int MAX_LENGTH = 8192;

    public static final Type<AdminActionPayload> TYPE =
            VanillaCompat.payloadType(OnTimeConstants.MOD_ID, "admin_action");

    public static final StreamCodec<ByteBuf, AdminActionPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AdminActionPayload decode(ByteBuf buffer) {
            return new AdminActionPayload(ByteBufCodecs.stringUtf8(MAX_LENGTH).decode(buffer));
        }

        @Override
        public void encode(ByteBuf buffer, AdminActionPayload payload) {
            ByteBufCodecs.stringUtf8(MAX_LENGTH).encode(buffer, payload.json());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
