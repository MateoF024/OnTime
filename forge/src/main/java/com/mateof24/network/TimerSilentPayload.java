package com.mateof24.network;

import com.mateof24.OnTimeConstants;
import com.mateof24.compat.VanillaCompat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record TimerSilentPayload(boolean silent) implements CustomPacketPayload {

    public static final Type<TimerSilentPayload> TYPE =
            VanillaCompat.payloadType(OnTimeConstants.MOD_ID, "timer_silent");

    public static final StreamCodec<ByteBuf, TimerSilentPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TimerSilentPayload decode(ByteBuf buffer) {
            return new TimerSilentPayload(buffer.readBoolean());
        }

        @Override
        public void encode(ByteBuf buffer, TimerSilentPayload payload) {
            buffer.writeBoolean(payload.silent());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}