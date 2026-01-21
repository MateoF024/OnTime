package com.mateof24.network;

import com.mateof24.OnTimeConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TimerSyncPayload(
        String name,
        long currentTicks,
        long targetTicks,
        boolean countUp,
        boolean running,
        boolean silent,
        long serverTick
) implements CustomPacketPayload {

    public static final Type<TimerSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OnTimeConstants.MOD_ID, "timer_sync")
    );

    public static final StreamCodec<ByteBuf, TimerSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TimerSyncPayload decode(ByteBuf buffer) {
            String name = decodeString(buffer);
            long currentTicks = buffer.readLong();
            long targetTicks = buffer.readLong();
            boolean countUp = buffer.readBoolean();
            boolean running = buffer.readBoolean();
            boolean silent = buffer.readBoolean();
            long serverTick = buffer.readLong();

            return new TimerSyncPayload(name, currentTicks, targetTicks, countUp, running, silent, serverTick);
        }

        @Override
        public void encode(ByteBuf buffer, TimerSyncPayload payload) {
            encodeString(buffer, payload.name());
            buffer.writeLong(payload.currentTicks());
            buffer.writeLong(payload.targetTicks());
            buffer.writeBoolean(payload.countUp());
            buffer.writeBoolean(payload.running());
            buffer.writeBoolean(payload.silent());
            buffer.writeLong(payload.serverTick());
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