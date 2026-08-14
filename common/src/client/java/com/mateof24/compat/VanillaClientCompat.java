package com.mateof24.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Client-only counterpart of {@link VanillaCompat}, kept in a separate class so
 * dedicated servers never classload client types.
 *
 * <p>1.20.1. One line differs from the one on 'main': the id is built with the
 * constructor, because {@code ResourceLocation.parse} does not exist yet —
 * verified against the mapped jar, which has {@code ResourceLocation(String)}
 * and no {@code parse}. Everything else, including the fallback sound being a
 * {@code Holder.Reference} that has to be unwrapped with {@code value()}, is
 * the same here as there.</p>
 */
public final class VanillaClientCompat {

    private VanillaClientCompat() {}

    /**
     * Plays the timer tick sound locally at the player's position, falling back
     * to the vanilla note-block hat sound when the configured id is invalid.
     * Callers must ensure {@code mc.player} and {@code mc.level} are non-null.
     */
    public static void playLocalTimerSound(String soundId, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        try {
            SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(new ResourceLocation(soundId));
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    soundEvent, SoundSource.MASTER, volume, pitch, false);
        } catch (Exception e) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.MASTER, 0.75F, 2.0F, false);
        }
    }
}
