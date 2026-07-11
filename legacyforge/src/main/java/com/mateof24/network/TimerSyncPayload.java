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
    private final String titleAbove;
    private final String titleBelow;
    private final String titleLeft;
    private final String titleRight;

    public TimerSyncPayload(String name, long currentTicks, long targetTicks,
                            boolean countUp, boolean running, boolean silent,
                            String titleAbove, String titleBelow,
                            String titleLeft, String titleRight) {
        this.name = name;
        this.currentTicks = currentTicks;
        this.targetTicks = targetTicks;
        this.countUp = countUp;
        this.running = running;
        this.silent = silent;
        this.titleAbove = titleAbove;
        this.titleBelow = titleBelow;
        this.titleLeft = titleLeft;
        this.titleRight = titleRight;
    }

    public static void encode(TimerSyncPayload msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.name);
        buf.writeLong(msg.currentTicks);
        buf.writeLong(msg.targetTicks);
        buf.writeBoolean(msg.countUp);
        buf.writeBoolean(msg.running);
        buf.writeBoolean(msg.silent);
        buf.writeUtf(msg.titleAbove);
        buf.writeUtf(msg.titleBelow);
        buf.writeUtf(msg.titleLeft);
        buf.writeUtf(msg.titleRight);
    }

    public static TimerSyncPayload decode(FriendlyByteBuf buf) {
        String name = buf.readUtf();
        long currentTicks = buf.readLong();
        long targetTicks = buf.readLong();
        boolean countUp = buf.readBoolean();
        boolean running = buf.readBoolean();
        boolean silent = buf.readBoolean();
        String titleAbove = buf.readUtf();
        String titleBelow = buf.readUtf();
        String titleLeft = buf.readUtf();
        String titleRight = buf.readUtf();

        return new TimerSyncPayload(name, currentTicks, targetTicks, countUp, running, silent,
                titleAbove, titleBelow, titleLeft, titleRight);
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
                        msg.silent
                );
                com.mateof24.render.ClientTimerState.updateTitles(
                        msg.titleAbove, msg.titleBelow, msg.titleLeft, msg.titleRight);
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
}