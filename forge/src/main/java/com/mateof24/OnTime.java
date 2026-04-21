package com.mateof24;

import com.mateof24.command.TimerCommands;
import com.mateof24.config.ModConfig;
import com.mateof24.manager.TimerManager;
import com.mateof24.network.NetworkHandler;
import com.mateof24.platform.Services;
import com.mateof24.storage.PlayerPreferences;
import com.mateof24.tick.TimerTickHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.fml.event.lifecycle.InterModProcessEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Mod(OnTimeConstants.MOD_ID)
public class OnTime {
    public static final Logger LOGGER = LoggerFactory.getLogger(OnTimeConstants.MOD_ID);
    private static MinecraftServer serverInstance = null;

    public OnTime(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::processIMC);

        IEventBus forgeEventBus = NeoForge.EVENT_BUS;
        forgeEventBus.addListener(this::onRegisterCommands);
        forgeEventBus.addListener(this::onServerTick);
        forgeEventBus.addListener(this::onServerStarted);
        forgeEventBus.addListener(this::onServerStopping);
        forgeEventBus.addListener(this::onPlayerJoin);
        forgeEventBus.addListener(this::onPlayerDeath);
        forgeEventBus.addListener(this::onDimensionChange);
        forgeEventBus.addListener(this::onAdvancementEarned);
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
        serverInstance = event.getServer();
        ModConfig.onSaveHook = () -> Services.PLATFORM.sendDisplayConfigPacketToAll(serverInstance);

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

    private void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        UUID playerUUID = player.getUUID();
        NetworkHandler.syncVisibilityToClient(player, PlayerPreferences.getTimerVisibility(playerUUID));
        NetworkHandler.syncSilentToClient(player, PlayerPreferences.getTimerSilent(playerUUID));
        Services.PLATFORM.sendDisplayConfigPacket(player);

        TimerManager.getInstance().getActiveTimer().ifPresent(timer -> {
            MinecraftServer server = player.getServer();
            if (server != null) {
                NetworkHandler.syncTimerToClient(player, timer.getName(), timer.getCurrentTicks(),
                        timer.getTargetTicks(), timer.isCountUp(), timer.isRunning(),
                        timer.isSilent(), server.getTickCount());
            }
        });
    }

    private void onServerStopping(ServerStoppingEvent event) {
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

    private void onPlayerDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer)) return;
        fireTrigger("player_death", null);
    }
    private void onDimensionChange(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        fireTrigger("dimension_change", event.getTo().location().toString());
    }
    private void onAdvancementEarned(net.neoforged.neoforge.event.entity.player.AdvancementEarnEvent event) {
        fireTrigger("advancement", event.getAdvancement().id().toString());
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