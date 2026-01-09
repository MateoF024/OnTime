package com.mateof24;

import com.mateof24.command.TimerCommands;
import com.mateof24.config.ModConfig;
import com.mateof24.manager.TimerManager;
import com.mateof24.network.NetworkHandler;
import com.mateof24.storage.PlayerPreferences;
import com.mateof24.tick.TimerTickHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class OnTime implements ModInitializer {
    public static final String MOD_ID = "ontime";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModConfig.getInstance().load();
        PlayerPreferences.load();
        NetworkHandler.registerPackets();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            TimerCommands.register(dispatcher);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            TimerTickHandler.tick(server);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            TimerManager.getInstance().loadTimers();

            TimerManager.getInstance().getActiveTimer().ifPresent(timer -> {
                if (timer.wasRunningBeforeShutdown()) {
                    timer.setRunning(true);
                    timer.setWasRunningBeforeShutdown(false);
                    TimerManager.getInstance().saveTimers();
                    LOGGER.info("Timer '{}' auto-resumed after server restart at {}",
                            timer.getName(),
                            timer.getFormattedTime());
                } else {
                    LOGGER.info("Active timer loaded: '{}' at {} (paused)",
                            timer.getName(),
                            timer.getFormattedTime());
                }
            });
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            UUID playerUUID = handler.getPlayer().getUUID();
            boolean visible = PlayerPreferences.getTimerVisibility(playerUUID);
            NetworkHandler.syncVisibilityToClient(handler.getPlayer(), visible);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            TimerManager.getInstance().getActiveTimer().ifPresent(timer -> {
                timer.setWasRunningBeforeShutdown(timer.isRunning());
                if (timer.isRunning()) {
                    timer.setRunning(false);
                    LOGGER.info("Timer '{}' paused due to server shutdown", timer.getName());
                }
            });
            TimerManager.getInstance().saveTimers();
            LOGGER.info("Timers saved on server shutdown");
        });

        LOGGER.info("OnTime mod initialized successfully!");
    }
}