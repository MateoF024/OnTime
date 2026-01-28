package com.mateof24;

import com.mateof24.command.TimerCommands;
import com.mateof24.config.ModConfig;
import com.mateof24.manager.TimerManager;
import com.mateof24.network.NetworkHandler;
import com.mateof24.platform.Services;
import com.mateof24.storage.PlayerPreferences;
import com.mateof24.tick.TimerTickHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Mod(OnTimeConstants.MOD_ID)
public class OnTime {
    public static final Logger LOGGER = LoggerFactory.getLogger(OnTimeConstants.MOD_ID);

    public OnTime() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModConfig.getInstance().load();
            PlayerPreferences.load();
            NetworkHandler.registerPackets();
            LOGGER.info("OnTime mod initialized (Forge 1.20.1)");
        });
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
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

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
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

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        TimerCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            TimerTickHandler.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();
            boolean visible = PlayerPreferences.getTimerVisibility(playerUUID);
            Services.PLATFORM.sendVisibilityPacket(player, visible);

            TimerManager.getInstance().getActiveTimer().ifPresent(timer -> {
                Services.PLATFORM.sendTimerSyncPacket(
                        player.getServer(),
                        timer.getName(),
                        timer.getCurrentTicks(),
                        timer.getTargetTicks(),
                        timer.isCountUp(),
                        timer.isRunning(),
                        timer.isSilent()
                );
            });
        }
    }
}