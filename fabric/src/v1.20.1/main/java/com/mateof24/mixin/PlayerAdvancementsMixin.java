package com.mateof24.mixin;

import com.mateof24.trigger.TriggerDispatcher;
import net.minecraft.advancements.Advancement;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The same mixin 'main' carries, one type apart: 1.20.1's {@code award} takes
 * an {@link Advancement} rather than an {@code AdvancementHolder}, and the id
 * comes off it with {@code getId()} rather than {@code id()} — verified against
 * the mapped jar, which declares {@code award(Advancement, String)}. The two
 * injections and the reason for both are unchanged.
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {

    @Unique
    private boolean ontime$wasDoneBeforeAward;

    @Inject(method = "award", at = @At("HEAD"))
    private void ontime$captureWasDone(Advancement advancement, String criterion,
                                       CallbackInfoReturnable<Boolean> cir) {
        PlayerAdvancements self = (PlayerAdvancements) (Object) this;
        ontime$wasDoneBeforeAward = self.getOrStartProgress(advancement).isDone();
    }

    /**
     * Who earned it.
     *
     * <p>Needed because a trigger can be watching particular people, so an
     * advancement with nobody attached would reach every trigger of that
     * kind.</p>
     */
    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At("RETURN"))
    private void ontime$fireOnEarn(Advancement advancement, String criterion,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (ontime$wasDoneBeforeAward) return;
        PlayerAdvancements self = (PlayerAdvancements) (Object) this;
        if (!self.getOrStartProgress(advancement).isDone()) return;
        TriggerDispatcher.dispatch(com.mateof24.trigger.Trigger.Kind.ADVANCEMENT,
                advancement.getId().toString(), this.player);
    }
}
