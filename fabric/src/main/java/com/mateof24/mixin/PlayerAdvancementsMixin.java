package com.mateof24.mixin;

import com.mateof24.trigger.TriggerDispatcher;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {

    @Unique
    private boolean ontime$wasDoneBeforeAward;

    @Inject(method = "award", at = @At("HEAD"))
    private void ontime$captureWasDone(AdvancementHolder advancement, String criterion,
                                       CallbackInfoReturnable<Boolean> cir) {
        PlayerAdvancements self = (PlayerAdvancements) (Object) this;
        ontime$wasDoneBeforeAward = self.getOrStartProgress(advancement).isDone();
    }

    /**
     * Who earned it.
     *
     * <p>Checked with javap against 1.21.1, 1.21.6 and 26.2: the field is
     * there in all three. It is needed because a trigger can now be watching
     * particular people, so an advancement with nobody attached would reach
     * every trigger of that kind.</p>
     */
    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At("RETURN"))
    private void ontime$fireOnEarn(AdvancementHolder advancement, String criterion,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (ontime$wasDoneBeforeAward) return;
        PlayerAdvancements self = (PlayerAdvancements) (Object) this;
        if (!self.getOrStartProgress(advancement).isDone()) return;
        TriggerDispatcher.dispatch(com.mateof24.trigger.Trigger.Kind.ADVANCEMENT,
                advancement.id().toString(), this.player);
    }
}
