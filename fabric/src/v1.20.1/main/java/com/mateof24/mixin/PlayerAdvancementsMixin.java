package com.mateof24.mixin;

import com.mateof24.trigger.TriggerDispatcher;
import net.minecraft.advancements.Advancement;
import net.minecraft.server.PlayerAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    @Inject(method = "award", at = @At("RETURN"))
    private void ontime$fireOnEarn(Advancement advancement, String criterion,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (ontime$wasDoneBeforeAward) return;
        PlayerAdvancements self = (PlayerAdvancements) (Object) this;
        if (!self.getOrStartProgress(advancement).isDone()) return;
        TriggerDispatcher.dispatch("advancement", advancement.getId().toString());
    }
}
