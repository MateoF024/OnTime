package com.mateof24;

import com.mateof24.command.TimerCommands;
import com.mateof24.config.ModConfig;
import com.mateof24.manager.TimerManager;
import com.mateof24.network.NetworkHandler;
import com.mateof24.storage.PlayerPreferences;
import com.mateof24.tick.TimerTickHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Mod(OnTimeConstants.MOD_ID)
public class OnTime {
    public static final Logger LOGGER = LoggerFactory.getLogger(OnTimeConstants.MOD_ID);

    public OnTime(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);

        IEventBus forgeEventBus = NeoForge.EVENT_BUS;
        forgeEventBus.addListener(this::onRegisterCommands);
        forgeEventBus.addListener(this::onServerTick);
        forgeEventBus.addListener(this::onServerStarted);
        forgeEventBus.addListener(this::onServerStopping);
        forgeEventBus.addListener(this::onPlayerJoin);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        ModConfig.getInstance().load();
        PlayerPreferences.load();
        LOGGER.info("OnTime NeoForge mod initialized successfully!");
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        NetworkHandler.registerPayloads(event);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        TimerCommands.register(event.getDispatcher());
    }

    private void onServerTick(ServerTickEvent.Post event) {
        TimerTickHandler.tick(event.getServer());
    }

    private void onServerStarted(ServerStartedEvent event) {
        TimerManager.getInstance().loadTimers();

        TimerManager.getInstance().getActiveTimer().ifPresent(timer -> {
            if (timer.wasRunningBeforeShutdown()) {
                timer.setRunning(true);
                timer.setWasRunningBeforeShutdown(false);
                TimerManager.getInstance().saveTimers();
                LOGGER.info("Timer '{}' auto-resumed after server restart at {}",
                        timer.getName(), timer.getFormattedTime());
            } else {
                LOGGER.info("Active timer loaded: '{}' at {} (paused)",
                        timer.getName(), timer.getFormattedTime());
            }
        });
    }

    private void onPlayerJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();
            boolean visible = PlayerPreferences.getTimerVisibility(playerUUID);
            NetworkHandler.syncVisibilityToClient(player, visible);

            TimerManager.getInstance().getActiveTimer().ifPresent(timer -> {
                MinecraftServer server = player.level().getServer();
                if (server != null) {
                    NetworkHandler.syncTimerToClients(
                            server,
                            timer.getName(),
                            timer.getCurrentTicks(),
                            timer.getTargetTicks(),
                            timer.isCountUp(),
                            timer.isRunning(),
                            timer.isSilent()
                    );
                }
            });
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        TimerManager.getInstance().getActiveTimer().ifPresent(timer -> {
            timer.setWasRunningBeforeShutdown(timer.isRunning());
            if (timer.isRunning()) {
                timer.setRunning(false);
                LOGGER.info("Timer '{}' paused due to server shutdown", timer.getName());
            }
        });
        TimerManager.getInstance().saveTimers();
        LOGGER.info("Timers saved on server shutdown");
    }
}
