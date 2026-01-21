package com.mateof24;

import com.mateof24.config.ClientConfig;
import com.mateof24.config.ConfigScreen;
import com.mateof24.network.ClientTimerState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.fml.ModLoadingContext;

@EventBusSubscriber(modid = OnTimeConstants.MOD_ID, value = Dist.CLIENT)
public class OnTimeClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ClientConfig.getInstance().load();

        ModLoadingContext.get().registerExtensionPoint(
                IConfigScreenFactory.class,
                () -> (mc, parent) -> ConfigScreen.createConfigScreen(parent)
        );

        NeoForge.EVENT_BUS.addListener(OnTimeClient::onRenderGui);
        NeoForge.EVENT_BUS.addListener(OnTimeClient::onClientTick);
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        com.mateof24.render.TimerRenderer.render(event.getGuiGraphics(), event.getPartialTick());
    }

    private static void onClientTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide()) {
            ClientTimerState.tick();
        }
    }
}