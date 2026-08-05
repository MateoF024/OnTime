package com.mateof24.network;

import com.mateof24.OnTimeConstants;
import com.mateof24.compat.VanillaCompat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The admin panel's whole state, as one JSON document. */
public record AdminStatePayload(String json) implements CustomPacketPayload {

    /** Generous: it carries every timer on the server. */
    private static final int MAX_LENGTH = 1 << 20;

    public static final Type<AdminStatePayload> TYPE =
            VanillaCompat.payloadType(OnTimeConstants.MOD_ID, "admin_state");

    public static final StreamCodec<ByteBuf, AdminStatePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AdminStatePayload decode(ByteBuf buffer) {
            return new AdminStatePayload(ByteBufCodecs.stringUtf8(MAX_LENGTH).decode(buffer));
        }

        @Override
        public void encode(ByteBuf buffer, AdminStatePayload payload) {
            ByteBufCodecs.stringUtf8(MAX_LENGTH).encode(buffer, payload.json());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
