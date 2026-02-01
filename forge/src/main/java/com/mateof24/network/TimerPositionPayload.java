package com.mateof24.network;

import com.mateof24.OnTimeConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TimerPositionPayload(String presetName) implements CustomPacketPayload {

    public static final Type<TimerPositionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OnTimeConstants.MOD_ID, "timer_position")
    );

    public static final StreamCodec<ByteBuf, TimerPositionPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TimerPositionPayload decode(ByteBuf buffer) {
            String presetName = decodeString(buffer);
            return new TimerPositionPayload(presetName);
        }

        @Override
        public void encode(ByteBuf buffer, TimerPositionPayload payload) {
            encodeString(buffer, payload.presetName());
        }

        private void encodeString(ByteBuf buffer, String str) {
            byte[] bytes = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            buffer.writeInt(bytes.length);
            buffer.writeBytes(bytes);
        }

        private String decodeString(ByteBuf buffer) {
            int length = buffer.readInt();
            byte[] bytes = new byte[length];
            buffer.readBytes(bytes);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}