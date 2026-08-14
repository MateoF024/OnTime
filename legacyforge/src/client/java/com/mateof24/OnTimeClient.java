package com.mateof24;

import com.mateof24.network.NetworkHandler;
import com.mateof24.render.ClientTimerState;
import com.mateof24.render.TimerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * The Forge client entry point, doing what the Fabric one does in the shape
 * Forge 1.20.1 asks for: a static subscriber on the mod bus, and listeners
 * added to the game bus once client setup has run.
 *
 * <p>The Cloth Config screen it used to register is gone — 5.0.0 retired Cloth
 * and ModMenu outright, and the settings live in the panel now. What replaces
 * it is the panel's own two ends, the same two the Fabric side registers.</p>
 */
@Mod.EventBusSubscriber(modid = OnTimeConstants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class OnTimeClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            com.mateof24.gui.AdminScreen.register();
            com.mateof24.gui.AdminClientState.setSender(NetworkHandler::sendAdminAction);

            MinecraftForge.EVENT_BUS.addListener(OnTimeClient::onRenderGuiOverlay);
            MinecraftForge.EVENT_BUS.addListener(OnTimeClient::onClientTick);
            MinecraftForge.EVENT_BUS.addListener(OnTimeClient::onDisconnect);
        });
    }

    private static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        TimerRenderer.render(event.getGuiGraphics(), event.getPartialTick());
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientTimerState.tick();
            com.mateof24.integration.JadeClientHook.updateFromTimer();
        }
    }

    /** Server state does not outlive the connection. */
    private static void onDisconnect(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        ClientTimerState.clear();
        com.mateof24.gui.AdminClientState.clear();
        com.mateof24.integration.JadeOverlayManager.resetOnDisconnect();
    }
}
