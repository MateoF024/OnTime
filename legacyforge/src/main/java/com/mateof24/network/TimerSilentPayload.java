package com.mateof24.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TimerSilentPayload {
    private final boolean silent;

    public TimerSilentPayload(boolean silent) {
        this.silent = silent;
    }

    public static void encode(TimerSilentPayload msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.silent);
    }

    public static TimerSilentPayload decode(FriendlyByteBuf buf) {
        return new TimerSilentPayload(buf.readBoolean());
    }

    public static void handle(TimerSilentPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            com.mateof24.render.ClientTimerState.setPlayerSilent(msg.silent);
        });
        ctx.get().setPacketHandled(true);
    }

    public boolean silent() { return silent; }
}