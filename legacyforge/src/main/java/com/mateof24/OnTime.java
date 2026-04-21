package com.mateof24;

import com.mateof24.command.TimerCommands;
import com.mateof24.config.ModConfig;
import com.mateof24.manager.TimerManager;
import com.mateof24.platform.Services;
import com.mateof24.storage.PlayerPreferences;
import com.mateof24.tick.TimerTickHandler;
import net.minecraft.server.MinecraftServer;
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
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Mod(OnTimeConstants.MOD_ID)
public class OnTime {
    public static final Logger LOGGER = LoggerFactory.getLogger(OnTimeConstants.MOD_ID);
    private static MinecraftServer serverInstance = null;

    public OnTime() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::processIMC);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModConfig.getInstance().load();
            PlayerPreferences.load();
            Services.PLATFORM.registerPackets();
            LOGGER.info("OnTime mod initialized (Forge 1.20.1)");
        });
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        serverInstance = event.getServer();
        ModConfig.onSaveHook = () -> Services.PLATFORM.sendDisplayConfigPacketToAll(serverInstance);
        com.mateof24.integration.FTBQuestsIntegration.tryInit();
        com.mateof24.integration.WorldProtectorIntegration.tryInit();

        TimerManager.getInstance().loadTimers();
        if (ModConfig.getInstance().isWebSocketEnabled()) {
            com.mateof24.websocket.TimerWebSocketServer.getInstance()
                    .start(ModConfig.getInstance().getWebSocketPort());
        }
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
        com.mateof24.websocket.TimerWebSocketServer.getInstance().stop();
        com.mateof24.webpanel.TimerWebPanel.getInstance().stop();
        ModConfig.onSaveHook = null;
        serverInstance = null;

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
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        UUID playerUUID = player.getUUID();
        Services.PLATFORM.sendVisibilityPacket(player, PlayerPreferences.getTimerVisibility(playerUUID));
        Services.PLATFORM.sendSilentPacket(player, PlayerPreferences.getTimerSilent(playerUUID));
        Services.PLATFORM.sendDisplayConfigPacket(player);

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

    private void processIMC(InterModProcessEvent event) {
        event.getIMCStream()
                .filter(msg -> msg.method().equals("register"))
                .forEach(msg -> {
                    try {
                        Object supplier = msg.messageSupplier().get();
                        if (supplier instanceof com.mateof24.api.OnTimeEntrypoint ep) {
                            ep.onOntimeInitialize(com.mateof24.api.OnTimeAPI.getInstance());
                        }
                    } catch (Exception e) {
                        LOGGER.warn("OnTime IMC entrypoint failed from mod: {}", msg.senderModId(), e);
                    }
                });
    }

    @SubscribeEvent
    public void onPlayerDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer)) return;
        fireTrigger("player_death", null);
    }
    @SubscribeEvent
    public void onDimensionChange(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        fireTrigger("dimension_change", event.getTo().location().toString());
    }
    @SubscribeEvent
    public void onAdvancementEarned(net.minecraftforge.event.advancements.AdvancementEarnEvent event) {
        fireTrigger("advancement", event.getAdvancement().getId().toString());
    }

    private static void fireTrigger(String type, String param) {
        com.mateof24.manager.TimerManager.getInstance().getActiveTimer().ifPresent(t -> {
            String trigger = t.getTriggerType();
            if (trigger == null) return;
            if (trigger.equals(type) || (param != null && trigger.equals(type + ":" + param))) {
                com.mateof24.trigger.TriggerRegistry.fire();
            }
        });
        com.mateof24.manager.TimerManager.getInstance().getAllTimers().values().stream()
                .filter(t -> !t.isRunning() && "start".equals(t.getTriggerAction()))
                .forEach(t -> {
                    String trigger = t.getTriggerType();
                    if (trigger == null) return;
                    if (trigger.equals(type) || (param != null && trigger.equals(type + ":" + param))) {
                        com.mateof24.trigger.TriggerRegistry.fire();
                    }
                });
    }

}