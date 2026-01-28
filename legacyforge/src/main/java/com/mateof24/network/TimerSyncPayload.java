package com.mateof24.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TimerSyncPayload {
    private final String name;
    private final long currentTicks;
    private final long targetTicks;
    private final boolean countUp;
    private final boolean running;
    private final boolean silent;
    private final long serverTick;

    public TimerSyncPayload(String name, long currentTicks, long targetTicks,
                            boolean countUp, boolean running, boolean silent, long serverTick) {
        this.name = name;
        this.currentTicks = currentTicks;
        this.targetTicks = targetTicks;
        this.countUp = countUp;
        this.running = running;
        this.silent = silent;
        this.serverTick = serverTick;
    }

    public static void encode(TimerSyncPayload msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.name);
        buf.writeLong(msg.currentTicks);
        buf.writeLong(msg.targetTicks);
        buf.writeBoolean(msg.countUp);
        buf.writeBoolean(msg.running);
        buf.writeBoolean(msg.silent);
        buf.writeLong(msg.serverTick);
    }

    public static TimerSyncPayload decode(FriendlyByteBuf buf) {
        String name = buf.readUtf();
        long currentTicks = buf.readLong();
        long targetTicks = buf.readLong();
        boolean countUp = buf.readBoolean();
        boolean running = buf.readBoolean();
        boolean silent = buf.readBoolean();
        long serverTick = buf.readLong();

        return new TimerSyncPayload(name, currentTicks, targetTicks, countUp, running, silent, serverTick);
    }

    public static void handle(TimerSyncPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (msg.name.isEmpty()) {
                com.mateof24.render.ClientTimerState.clear();
            } else {
                com.mateof24.render.ClientTimerState.updateTimer(
                        msg.name,
                        msg.currentTicks,
                        msg.targetTicks,
                        msg.countUp,
                        msg.running,
                        msg.silent,
                        msg.serverTick
                );
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public String name() { return name; }
    public long currentTicks() { return currentTicks; }
    public long targetTicks() { return targetTicks; }
    public boolean countUp() { return countUp; }
    public boolean running() { return running; }
    public boolean silent() { return silent; }
    public long serverTick() { return serverTick; }
}