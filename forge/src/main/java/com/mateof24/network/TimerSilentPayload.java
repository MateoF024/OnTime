package com.mateof24.network;

import com.mateof24.OnTimeConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TimerSilentPayload(boolean silent) implements CustomPacketPayload {

    public static final Type<TimerSilentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OnTimeConstants.MOD_ID, "timer_silent")
    );

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