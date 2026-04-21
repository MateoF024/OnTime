package com.mateof24.trigger;

import com.mateof24.manager.TimerManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;

public class FabricTriggerHandler {

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer player)) return;
            checkTrigger("player_death", null, player.getServer());
        });

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {});

        try {
            net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
                    (player, origin, destination) -> checkTrigger("dimension_change",
                            destination.dimension().location().toString(), player.getServer())
            );
        } catch (NoClassDefFoundError ignored) {}
    }

    static void checkTrigger(String type, String param, net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        TimerManager.getInstance().getActiveTimer().ifPresent(timer -> {
            String trigger = timer.getTriggerType();
            if (trigger == null) return;
            if (trigger.equals(type) || (param != null && trigger.equals(type + ":" + param))) {
                TriggerRegistry.fire();
            }
        });
    }
}