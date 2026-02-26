package com.mateof24.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TimerPositionPayload {
    private final String presetName;

    public TimerPositionPayload(String presetName) {
        this.presetName = presetName;
    }

    public static void encode(TimerPositionPayload msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.presetName);
    }

    public static TimerPositionPayload decode(FriendlyByteBuf buf) {
        return new TimerPositionPayload(buf.readUtf());
    }

    public static void handle(TimerPositionPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            com.mateof24.config.ModConfig config = com.mateof24.config.ModConfig.getInstance();
            Minecraft mc = Minecraft.getInstance();

            com.mateof24.config.TimerPositionPreset preset =
                    com.mateof24.config.TimerPositionPreset.fromString(msg.presetName);

            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();

            String sampleText = "00:00:00";
            int textWidth = (int) (mc.font.width(sampleText) * config.getTimerScale());
            int textHeight = (int) (mc.font.lineHeight * config.getTimerScale());

            config.applyPreset(preset, screenWidth, screenHeight, textWidth, textHeight);
        });
        ctx.get().setPacketHandled(true);
    }

    public String presetName() { return presetName; }
}