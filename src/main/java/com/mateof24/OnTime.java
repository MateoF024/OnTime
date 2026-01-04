package com.mateof24;

import com.mateof24.command.TimerCommands;
import com.mateof24.manager.TimerManager;
import com.mateof24.network.NetworkHandler;
import com.mateof24.tick.TimerTickHandler;
import com.mateof24.timer.Timer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OnTime implements ModInitializer {
    public static final String MOD_ID = "ontime";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        NetworkHandler.registerPackets();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            TimerCommands.register(dispatcher);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            TimerTickHandler.tick(server);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            TimerManager.getInstance().loadTimers();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            TimerManager.getInstance().getActiveTimer().ifPresent(timer -> {
                if (timer.isRunning()) {
                    timer.setRunning(false);
                    LOGGER.info("Timer '{}' paused due to server shutdown", timer.getName());
                }
            });
            TimerManager.getInstance().saveTimers();
        });

        LOGGER.info("OnTime mod initialized successfully!");
    }
}