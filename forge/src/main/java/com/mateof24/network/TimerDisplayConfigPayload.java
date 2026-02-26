package com.mateof24.network;

import com.mateof24.OnTimeConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TimerDisplayConfigPayload(
        int timerX, int timerY, String positionPreset, float scale,
        int colorHigh, int colorMid, int colorLow,
        int thresholdMid, int thresholdLow,
        String soundId, float soundVolume, float soundPitch
) implements CustomPacketPayload {

    public static final Type<TimerDisplayConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OnTimeConstants.MOD_ID, "timer_display_config")
    );

    public static final StreamCodec<ByteBuf, TimerDisplayConfigPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TimerDisplayConfigPayload decode(ByteBuf buffer) {
            int timerX = buffer.readInt();
            int timerY = buffer.readInt();
            String preset = decodeString(buffer);
            float scale = buffer.readFloat();
            int colorHigh = buffer.readInt();
            int colorMid = buffer.readInt();
            int colorLow = buffer.readInt();
            int thresholdMid = buffer.readInt();
            int thresholdLow = buffer.readInt();
            String soundId = decodeString(buffer);
            float soundVolume = buffer.readFloat();
            float soundPitch = buffer.readFloat();
            return new TimerDisplayConfigPayload(timerX, timerY, preset, scale,
                    colorHigh, colorMid, colorLow, thresholdMid, thresholdLow,
                    soundId, soundVolume, soundPitch);
        }

        @Override
        public void encode(ByteBuf buffer, TimerDisplayConfigPayload payload) {
            buffer.writeInt(payload.timerX());
            buffer.writeInt(payload.timerY());
            encodeString(buffer, payload.positionPreset());
            buffer.writeFloat(payload.scale());
            buffer.writeInt(payload.colorHigh());
            buffer.writeInt(payload.colorMid());
            buffer.writeInt(payload.colorLow());
            buffer.writeInt(payload.thresholdMid());
            buffer.writeInt(payload.thresholdLow());
            encodeString(buffer, payload.soundId());
            buffer.writeFloat(payload.soundVolume());
            buffer.writeFloat(payload.soundPitch());
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