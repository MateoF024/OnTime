package com.mateof24.network;

import com.mateof24.OnTimeConstants;
import com.mateof24.compat.VanillaCompat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record TimerVisibilityPayload(boolean visible) implements CustomPacketPayload {

    public static final Type<TimerVisibilityPayload> TYPE =
            VanillaCompat.payloadType(OnTimeConstants.MOD_ID, "timer_visibility");

    public static final StreamCodec<ByteBuf, TimerVisibilityPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TimerVisibilityPayload decode(ByteBuf buffer) {
            return new TimerVisibilityPayload(buffer.readBoolean());
        }

        @Override
        public void encode(ByteBuf buffer, TimerVisibilityPayload payload) {
            buffer.writeBoolean(payload.visible());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}