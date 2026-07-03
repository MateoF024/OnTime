package com.mateof24.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Client-only counterpart of {@link VanillaCompat}, kept in a separate class so
 * dedicated servers never classload client types. Same contract: exactly one
 * implementation per compat source set, identical signatures across variants.
 *
 * <p>compat1 — MC 1.21.1-1.21.10: {@code ResourceLocation} naming.</p>
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
            SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(ResourceLocation.parse(soundId));
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    soundEvent, SoundSource.MASTER, volume, pitch, false);
        } catch (Exception e) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.MASTER, 0.75F, 2.0F, false);
        }
    }
}
