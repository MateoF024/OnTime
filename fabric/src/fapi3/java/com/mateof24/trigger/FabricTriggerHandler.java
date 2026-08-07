package com.mateof24.trigger;

import com.mateof24.compat.VanillaCompat;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.minecraft.server.level.ServerPlayer;

public class FabricTriggerHandler {

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer player)) return;
            TriggerDispatcher.dispatch(com.mateof24.trigger.Trigger.Kind.PLAYER_DEATH, null, player);
        });

        // Fabric API 26.1 renamed ServerEntityWorldChangeEvents to
        // ServerEntityLevelChangeEvents ("world" -> "level").
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) ->
                TriggerDispatcher.dispatch(com.mateof24.trigger.Trigger.Kind.DIMENSION_CHANGE, VanillaCompat.dimensionId(destination), player)
        );
    }
}
