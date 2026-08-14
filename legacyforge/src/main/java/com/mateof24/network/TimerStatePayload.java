package com.mateof24.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The full set of executions a player can see.
 *
 * <p>A snapshot rather than a delta: it is self-healing, and at roughly sixty
 * bytes per run the size never matters. The fields are written in the order the
 * payload codec on 'main' writes them, so the two loaders and the two branches
 * all speak one protocol.</p>
 *
 * <p>It replaces 4.0.0's {@code TimerSyncPayload}, which carried one timer.
 * The channel is new rather than reshaped, so a 4.x client never decodes a
 * packet whose meaning changed underneath it.</p>
 */
public class TimerStatePayload {

    private final List<RunView> runs;

    public TimerStatePayload(List<RunView> runs) {
        this.runs = runs;
    }

    public static void encode(TimerStatePayload msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.runs.size());
        for (RunView v : msg.runs) {
            buf.writeUUID(v.runId());
            buf.writeUtf(v.timerName());
            buf.writeLong(v.currentTicks());
            buf.writeLong(v.targetTicks());
            buf.writeBoolean(v.countUp());
            buf.writeBoolean(v.running());
            buf.writeBoolean(v.silent());
            buf.writeUtf(v.titleAbove()); buf.writeUtf(v.titleBelow());
            buf.writeUtf(v.titleLeft()); buf.writeUtf(v.titleRight());
            buf.writeUtf(v.preset());
            buf.writeVarInt(v.x()); buf.writeVarInt(v.y());
            buf.writeFloat(v.scale());
            buf.writeInt(v.colorHigh()); buf.writeInt(v.colorMid()); buf.writeInt(v.colorLow());
            buf.writeVarInt(v.thresholdMid()); buf.writeVarInt(v.thresholdLow());
            buf.writeUtf(v.soundId());
            buf.writeFloat(v.soundVolume()); buf.writeFloat(v.soundPitch());
        }
    }

    public static TimerStatePayload decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<RunView> runs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            runs.add(new RunView(
                    buf.readUUID(), buf.readUtf(), buf.readLong(), buf.readLong(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                    buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
                    buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(), buf.readFloat(), buf.readFloat()));
        }
        return new TimerStatePayload(runs);
    }

    public static void handle(TimerStatePayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> com.mateof24.render.ClientTimerState.applyState(msg.runs));
        ctx.get().setPacketHandled(true);
    }

    public List<RunView> runs() { return runs; }
}
