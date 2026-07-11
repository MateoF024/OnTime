package com.mateof24.network;

import com.mateof24.OnTimeConstants;
import com.mateof24.compat.VanillaCompat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record TimerSyncPayload(
        String name,
        long currentTicks,
        long targetTicks,
        boolean countUp,
        boolean running,
        boolean silent,
        String titleAbove,
        String titleBelow,
        String titleLeft,
        String titleRight
) implements CustomPacketPayload {

    public static final Type<TimerSyncPayload> TYPE =
            VanillaCompat.payloadType(OnTimeConstants.MOD_ID, "timer_sync");

    public static final StreamCodec<ByteBuf, TimerSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TimerSyncPayload decode(ByteBuf buffer) {
            String name = decodeString(buffer);
            long currentTicks = buffer.readLong();
            long targetTicks = buffer.readLong();
            boolean countUp = buffer.readBoolean();
            boolean running = buffer.readBoolean();
            boolean silent = buffer.readBoolean();
            String titleAbove = decodeString(buffer);
            String titleBelow = decodeString(buffer);
            String titleLeft = decodeString(buffer);
            String titleRight = decodeString(buffer);

            return new TimerSyncPayload(name, currentTicks, targetTicks, countUp, running, silent,
                    titleAbove, titleBelow, titleLeft, titleRight);
        }

        @Override
        public void encode(ByteBuf buffer, TimerSyncPayload payload) {
            encodeString(buffer, payload.name());
            buffer.writeLong(payload.currentTicks());
            buffer.writeLong(payload.targetTicks());
            buffer.writeBoolean(payload.countUp());
            buffer.writeBoolean(payload.running());
            buffer.writeBoolean(payload.silent());
            encodeString(buffer, payload.titleAbove());
            encodeString(buffer, payload.titleBelow());
            encodeString(buffer, payload.titleLeft());
            encodeString(buffer, payload.titleRight());
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