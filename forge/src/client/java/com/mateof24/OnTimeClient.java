package com.mateof24;


import com.mateof24.render.ClientTimerState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = OnTimeConstants.MOD_ID, value = Dist.CLIENT)
public class OnTimeClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // No config screen extension point any more: the mod's settings live on
        // the server and are edited from /timer gui, which cannot be opened
        // from the mod list because there is no state without a server.
        NeoForge.EVENT_BUS.addListener(OnTimeClient::onRenderGui);
        NeoForge.EVENT_BUS.addListener(OnTimeClient::onClientTick);
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        com.mateof24.render.TimerRenderer.render(event.getGuiGraphics(), event.getPartialTick());
    }

    private static void onClientTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide()) {
            ClientTimerState.tick();
            com.mateof24.integration.JadeClientHook.updateFromTimer();
        }
    }
}