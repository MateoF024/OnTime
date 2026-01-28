package com.mateof24.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TimerVisibilityPayload {
    private final boolean visible;

    public TimerVisibilityPayload(boolean visible) {
        this.visible = visible;
    }

    public static void encode(TimerVisibilityPayload msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.visible);
    }

    public static TimerVisibilityPayload decode(FriendlyByteBuf buf) {
        return new TimerVisibilityPayload(buf.readBoolean());
    }

    public static void handle(TimerVisibilityPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            com.mateof24.render.ClientTimerState.setVisible(msg.visible);
        });
        ctx.get().setPacketHandled(true);
    }

    public boolean visible() { return visible; }
}