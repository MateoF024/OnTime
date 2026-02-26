package com.mateof24;

import com.mateof24.command.TimerCommands;
import com.mateof24.config.ModConfig;
import com.mateof24.manager.TimerManager;
import com.mateof24.platform.Services;
import com.mateof24.storage.PlayerPreferences;
import com.mateof24.tick.TimerTickHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

public class OnTime implements ModInitializer {
    public static final String MOD_ID = "ontime";
    private static MinecraftServer serverInstance = null;

    @Override
    public void onInitialize() {
        ModConfig.getInstance().load();
        PlayerPreferences.load();
        Services.PLATFORM.registerPackets();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                TimerCommands.register(dispatcher));

        ServerTickEvents.END_SERVER_TICK.register(TimerTickHandler::tick);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            serverInstance = server;
            ModConfig.onSaveHook = () -> Services.PLATFORM.sendDisplayConfigPacketToAll(serverInstance);

            TimerManager.getInstance().loadTimers();
            TimerManager.getInstance().getActiveTimer().ifPresent(timer -> {
                if (timer.wasRunningBeforeShutdown()) {
                    timer.setRunning(true);
                    timer.setWasRunningBeforeShutdown(false);
                    TimerManager.getInstance().saveTimers();
                    OnTimeConstants.LOGGER.info("Timer '{}' auto-resumed after server restart at {}",
                            timer.getName(), timer.getFormattedTime());
                } else {
                    OnTimeConstants.LOGGER.info("Active timer loaded: '{}' at {} (paused)",
                            timer.getName(), timer.getFormattedTime());
                }
            });
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            UUID playerUUID = handler.getPlayer().getUUID();
            Services.PLATFORM.sendVisibilityPacket(handler.getPlayer(), PlayerPreferences.getTimerVisibility(playerUUID));
            Services.PLATFORM.sendSilentPacket(handler.getPlayer(), PlayerPreferences.getTimerSilent(playerUUID));
            Services.PLATFORM.sendDisplayConfigPacket(handler.getPlayer());

            TimerManager.getInstance().getActiveTimer().ifPresent(timer ->
                    Services.PLATFORM.sendTimerSyncPacket(
                            server,
                            timer.getName(),
                            timer.getCurrentTicks(),
                            timer.getTargetTicks(),
                            timer.isCountUp(),
                            timer.isRunning(),
                            timer.isSilent()
                    )
            );
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ModConfig.onSaveHook = null;
            serverInstance = null;

            TimerManager.getInstance().getActiveTimer().ifPresent(timer -> {
                timer.setWasRunningBeforeShutdown(timer.isRunning());
                if (timer.isRunning()) {
                    timer.setRunning(false);
                    OnTimeConstants.LOGGER.info("Timer '{}' paused due to server shutdown", timer.getName());
                }
            });
            TimerManager.getInstance().saveTimers();
            OnTimeConstants.LOGGER.info("Timers saved on server shutdown");
        });

        OnTimeConstants.LOGGER.info("OnTime mod initialized successfully!");
    }
}