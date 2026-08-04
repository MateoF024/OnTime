package com.mateof24.network;

import com.mateof24.OnTimeConstants;
import com.mateof24.compat.VanillaCompat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The full set of executions a player can see.
 *
 * <p>A snapshot rather than a delta: it is self-healing, and at roughly sixty
 * bytes per run the size never matters. The channel is renamed from the 4.0.0
 * one on purpose — a 4.x client then simply never receives it, instead of
 * decoding a payload whose shape changed underneath it.</p>
 */
public record TimerStatePayload(List<RunView> runs) implements CustomPacketPayload {

    public static final Type<TimerStatePayload> TYPE =
            VanillaCompat.payloadType(OnTimeConstants.MOD_ID, "timer_state");

    public static TimerStatePayload of(List<RunView> runs) {
        return new TimerStatePayload(runs);
    }

    public static final StreamCodec<ByteBuf, TimerStatePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TimerStatePayload decode(ByteBuf buffer) {
            int count = buffer.readInt();
            List<RunView> runs = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                UUID runId = new UUID(buffer.readLong(), buffer.readLong());
                runs.add(new RunView(
                        runId,
                        decodeString(buffer),
                        buffer.readLong(),
                        buffer.readLong(),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        decodeString(buffer), decodeString(buffer),
                        decodeString(buffer), decodeString(buffer),
                        decodeString(buffer),
                        buffer.readInt(), buffer.readInt(),
                        buffer.readFloat()));
            }
            return new TimerStatePayload(runs);
        }

        @Override
        public void encode(ByteBuf buffer, TimerStatePayload payload) {
            buffer.writeInt(payload.runs().size());
            for (RunView v : payload.runs()) {
                buffer.writeLong(v.runId().getMostSignificantBits());
                buffer.writeLong(v.runId().getLeastSignificantBits());
                encodeString(buffer, v.timerName());
                buffer.writeLong(v.currentTicks());
                buffer.writeLong(v.targetTicks());
                buffer.writeBoolean(v.countUp());
                buffer.writeBoolean(v.running());
                buffer.writeBoolean(v.silent());
                encodeString(buffer, v.titleAbove());
                encodeString(buffer, v.titleBelow());
                encodeString(buffer, v.titleLeft());
                encodeString(buffer, v.titleRight());
                encodeString(buffer, v.preset());
                buffer.writeInt(v.x());
                buffer.writeInt(v.y());
                buffer.writeFloat(v.scale());
            }
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
