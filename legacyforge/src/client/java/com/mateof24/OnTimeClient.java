package com.mateof24;

import com.mateof24.config.ClientConfig;
import com.mateof24.config.ConfigScreen;
import com.mateof24.render.ClientTimerState;
import com.mateof24.render.TimerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = OnTimeConstants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class OnTimeClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ClientConfig.getInstance().load();

            ModLoadingContext.get().registerExtensionPoint(
                    ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory(
                            (minecraft, screen) -> ConfigScreen.createConfigScreen(screen)
                    )
            );

            MinecraftForge.EVENT_BUS.addListener(OnTimeClient::onRenderGuiOverlay);
            MinecraftForge.EVENT_BUS.addListener(OnTimeClient::onClientTick);
        });
    }

    private static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        TimerRenderer.render(event.getGuiGraphics(), event.getPartialTick());
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientTimerState.tick();
        }
    }
}