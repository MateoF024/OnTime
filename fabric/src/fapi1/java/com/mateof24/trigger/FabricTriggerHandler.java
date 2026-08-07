package com.mateof24.trigger;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.minecraft.server.level.ServerPlayer;

public class FabricTriggerHandler {

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer player)) return;
            TriggerDispatcher.dispatch(com.mateof24.trigger.Trigger.Kind.PLAYER_DEATH, null, player.getUUID());
        });

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                TriggerDispatcher.dispatch(com.mateof24.trigger.Trigger.Kind.DIMENSION_CHANGE, destination.dimension().location().toString(), player.getUUID())
        );
    }
}
