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

/**
 * The Forge 47 entry point.
 *
 * <p>It does what the NeoForge one on 'main' does, in the shape Forge 1.20.1
 * asks for: the mod bus comes from {@code FMLJavaModLoadingContext} rather than
 * the constructor, game events are subscribed by annotation rather than by
 * method reference, ticking arrives as one event with a phase instead of a
 * {@code Post} class of its own, and the packets are registered in common setup
 * because there is no payload-registration event to wait for.</p>
 *
 * <p>What changed with 5.0.0 rather than with the loader: a joining player is
 * sent <em>their</em> view of the executions instead of the single active
 * timer, the display config no longer travels on its own channel — every
 * execution carries its own — and the three game events name the player they
 * happened to, because a trigger can be watching particular people.</p>
 */
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
        com.mateof24.integration.FTBQuestsIntegration.tryInit();

        TimerManager.getInstance().loadTimers();
        if (ModConfig.getInstance().isWebSocketEnabled()) {
            com.mateof24.websocket.TimerWebSocketServer.getInstance()
                    .start(ModConfig.getInstance().getWebSocketPort());
        }
        TimerManager.getInstance().getActiveTimer().ifPresent(timer -> {
            if (timer.wasRunningBeforeShutdown()) {
                TimerManager.getInstance().getActiveRun().ifPresent(run -> {
                    run.setRunning(true);
                    run.mirrorToTimer();
                });
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
        serverInstance = null;

        TimerManager.getInstance().getActiveTimer().ifPresent(timer -> {
            timer.setWasRunningBeforeShutdown(timer.isRunning());
            if (timer.isRunning()) {
                timer.setRunning(false);
                LOGGER.info("Timer '{}' paused due to server shutdown", timer.getName());
            }
        });
        TimerManager.getInstance().saveTimers();
        PlayerPreferences.flush();
        ModConfig.getInstance().flush();
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

        // The joiner gets their own view: global runs plus any fixed audience
        // they belong to.
        Services.PLATFORM.sendTimerState(player);
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
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        com.mateof24.trigger.TriggerDispatcher.dispatch(
                com.mateof24.trigger.Trigger.Kind.PLAYER_DEATH, null, player);
    }

    @SubscribeEvent
    public void onDimensionChange(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        com.mateof24.trigger.TriggerDispatcher.dispatch(
                com.mateof24.trigger.Trigger.Kind.DIMENSION_CHANGE,
                com.mateof24.compat.VanillaCompat.keyId(event.getTo()), player);
    }

    @SubscribeEvent
    public void onAdvancementEarned(net.minecraftforge.event.entity.player.AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        // getId() rather than id(): 1.20.1 has no AdvancementHolder, the same
        // difference the mixin and the probe carry.
        com.mateof24.trigger.TriggerDispatcher.dispatch(
                com.mateof24.trigger.Trigger.Kind.ADVANCEMENT,
                event.getAdvancement().getId().toString(), player);
    }
}
