package com.mateof24.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TimerDisplayConfigPayload {
    private final int timerX, timerY;
    private final String positionPreset;
    private final float scale;
    private final int colorHigh, colorMid, colorLow;
    private final int thresholdMid, thresholdLow;
    private final String soundId;
    private final float soundVolume, soundPitch;

    public TimerDisplayConfigPayload(int timerX, int timerY, String positionPreset, float scale,
                                     int colorHigh, int colorMid, int colorLow,
                                     int thresholdMid, int thresholdLow,
                                     String soundId, float soundVolume, float soundPitch) {
        this.timerX = timerX; this.timerY = timerY; this.positionPreset = positionPreset; this.scale = scale;
        this.colorHigh = colorHigh; this.colorMid = colorMid; this.colorLow = colorLow;
        this.thresholdMid = thresholdMid; this.thresholdLow = thresholdLow;
        this.soundId = soundId; this.soundVolume = soundVolume; this.soundPitch = soundPitch;
    }

    public static void encode(TimerDisplayConfigPayload msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.timerX); buf.writeInt(msg.timerY);
        buf.writeUtf(msg.positionPreset); buf.writeFloat(msg.scale);
        buf.writeInt(msg.colorHigh); buf.writeInt(msg.colorMid); buf.writeInt(msg.colorLow);
        buf.writeInt(msg.thresholdMid); buf.writeInt(msg.thresholdLow);
        buf.writeUtf(msg.soundId); buf.writeFloat(msg.soundVolume); buf.writeFloat(msg.soundPitch);
    }

    public static TimerDisplayConfigPayload decode(FriendlyByteBuf buf) {
        int timerX = buf.readInt(); int timerY = buf.readInt();
        String preset = buf.readUtf(); float scale = buf.readFloat();
        int colorHigh = buf.readInt(); int colorMid = buf.readInt(); int colorLow = buf.readInt();
        int thresholdMid = buf.readInt(); int thresholdLow = buf.readInt();
        String soundId = buf.readUtf(); float soundVolume = buf.readFloat(); float soundPitch = buf.readFloat();
        return new TimerDisplayConfigPayload(timerX, timerY, preset, scale,
                colorHigh, colorMid, colorLow, thresholdMid, thresholdLow, soundId, soundVolume, soundPitch);
    }

    public static void handle(TimerDisplayConfigPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                com.mateof24.render.ClientTimerState.updateDisplayConfig(
                        msg.timerX, msg.timerY, msg.positionPreset, msg.scale,
                        msg.colorHigh, msg.colorMid, msg.colorLow,
                        msg.thresholdMid, msg.thresholdLow,
                        msg.soundId, msg.soundVolume, msg.soundPitch
                )
        );
        ctx.get().setPacketHandled(true);
    }

    public int timerX() { return timerX; }
    public int timerY() { return timerY; }
    public String positionPreset() { return positionPreset; }
    public float scale() { return scale; }
    public int colorHigh() { return colorHigh; }
    public int colorMid() { return colorMid; }
    public int colorLow() { return colorLow; }
    public int thresholdMid() { return thresholdMid; }
    public int thresholdLow() { return thresholdLow; }
    public String soundId() { return soundId; }
    public float soundVolume() { return soundVolume; }
    public float soundPitch() { return soundPitch; }
}