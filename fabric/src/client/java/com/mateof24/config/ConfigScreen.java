package com.mateof24.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.mateof24.config.TimerPositionPreset;

public class ConfigScreen {

    public static Screen createConfigScreen(Screen parent) {
        ModConfig config = ModConfig.getInstance();
        ClientConfig clientConfig = ClientConfig.getInstance();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("ontime.config.title"));

        builder.setSavingRunnable(config::save);

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        ConfigCategory display = builder.getOrCreateCategory(Component.translatable("ontime.config.category.display"));

        display.addEntry(entryBuilder.startSelector(
                        Component.translatable("ontime.config.position_preset"),
                        TimerPositionPreset.values(),
                        clientConfig.getPositionPreset())
                .setDefaultValue(TimerPositionPreset.BOSSBAR)
                .setNameProvider(preset -> Component.literal(preset.getDisplayName()))
                .setTooltip(Component.translatable("ontime.config.position_preset.tooltip"))
                .setSaveConsumer(preset -> {
                    Minecraft mc = Minecraft.getInstance();
                    int screenWidth = mc.getWindow().getGuiScaledWidth();
                    int screenHeight = mc.getWindow().getGuiScaledHeight();

                    String timeText = "00:00"; // Texto de ejemplo para calcular tamaño
                    int textWidth = (int) (mc.font.width(timeText) * clientConfig.getTimerScale());
                    int textHeight = (int) (mc.font.lineHeight * clientConfig.getTimerScale());

                    clientConfig.applyPreset(preset, screenWidth, screenHeight, textWidth, textHeight);
                })
                .build());

        display.addEntry(entryBuilder.startIntField(Component.translatable("ontime.config.timer_x"), clientConfig.getTimerX())
                .setDefaultValue(-1)
                .setTooltip(Component.translatable(
                        clientConfig.getPositionPreset() == TimerPositionPreset.CUSTOM
                                ? "ontime.config.timer_x.tooltip"
                                : "ontime.config.timer_x.disabled"
                ))
                .setSaveConsumer(x -> {
                    if (clientConfig.getPositionPreset() == TimerPositionPreset.CUSTOM) {
                        clientConfig.setTimerX(x);
                    }
                })
                .build());

        display.addEntry(entryBuilder.startIntField(Component.translatable("ontime.config.timer_y"), clientConfig.getTimerY())
                .setDefaultValue(4)
                .setMin(0)
                .setTooltip(Component.translatable(
                        clientConfig.getPositionPreset() == TimerPositionPreset.CUSTOM
                                ? "ontime.config.timer_y.tooltip"
                                : "ontime.config.timer_y.disabled"
                ))
                .setSaveConsumer(y -> {
                    if (clientConfig.getPositionPreset() == TimerPositionPreset.CUSTOM) {
                        clientConfig.setTimerY(y);
                    }
                })
                .build());

        display.addEntry(entryBuilder.startFloatField(Component.translatable("ontime.config.timer_scale"), clientConfig.getTimerScale())  // ← CAMBIAR
                .setDefaultValue(1.0f)
                .setMin(0.1f)
                .setMax(5.0f)
                .setTooltip(Component.translatable("ontime.config.timer_scale.tooltip"))
                .setSaveConsumer(clientConfig::setTimerScale)  // ← CAMBIAR
                .build());

        ConfigCategory colors = builder.getOrCreateCategory(Component.translatable("ontime.config.category.colors"));

        colors.addEntry(entryBuilder.startColorField(Component.translatable("ontime.config.color_high"),
                        config.getColorHigh())
                .setDefaultValue(0xFFFFFF)
                .setTooltip(Component.translatable("ontime.config.color_high.tooltip"))
                .setSaveConsumer(config::setColorHigh)
                .build());

        colors.addEntry(entryBuilder.startColorField(Component.translatable("ontime.config.color_mid"),
                        config.getColorMid())
                .setDefaultValue(0xFFFF00)
                .setTooltip(Component.translatable("ontime.config.color_mid.tooltip"))
                .setSaveConsumer(config::setColorMid)
                .build());

        colors.addEntry(entryBuilder.startColorField(Component.translatable("ontime.config.color_low"),
                        config.getColorLow())
                .setDefaultValue(0xFF0000)
                .setTooltip(Component.translatable("ontime.config.color_low.tooltip"))
                .setSaveConsumer(config::setColorLow)
                .build());

        colors.addEntry(entryBuilder.startIntSlider(Component.translatable("ontime.config.threshold_mid"),
                        config.getThresholdMid(), 0, 100)
                .setDefaultValue(30)
                .setTooltip(Component.translatable("ontime.config.threshold_mid.tooltip"))
                .setSaveConsumer(config::setThresholdMid)
                .build());

        colors.addEntry(entryBuilder.startIntSlider(Component.translatable("ontime.config.threshold_low"),
                        config.getThresholdLow(), 0, 100)
                .setDefaultValue(10)
                .setTooltip(Component.translatable("ontime.config.threshold_low.tooltip"))
                .setSaveConsumer(config::setThresholdLow)
                .build());

        ConfigCategory server = builder.getOrCreateCategory(Component.translatable("ontime.config.category.server"));

        server.addEntry(entryBuilder.startIntSlider(Component.translatable("ontime.config.permission_level"),
                        config.getRequiredPermissionLevel(), 0, 4)
                .setDefaultValue(2)
                .setTooltip(Component.translatable("ontime.config.permission_level.tooltip"))
                .setSaveConsumer(config::setRequiredPermissionLevel)
                .build());

        server.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("ontime.config.allow_players_hide"),
                        config.getAllowPlayersUseHide())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("ontime.config.allow_players_hide.tooltip"))
                .setSaveConsumer(config::setAllowPlayersUseHide)
                .build());

        server.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("ontime.config.allow_players_list"),
                        config.getAllowPlayersUseList())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("ontime.config.allow_players_list.tooltip"))
                .setSaveConsumer(config::setAllowPlayersUseList)
                .build());

        server.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("ontime.config.allow_players_silent"),
                        config.getAllowPlayersUseSilent())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("ontime.config.allow_players_silent.tooltip"))
                .setSaveConsumer(config::setAllowPlayersUseSilent)
                .build());

        server.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("ontime.config.allow_players_position"),
                        config.getAllowPlayersChangePosition())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("ontime.config.allow_players_position.tooltip"))
                .setSaveConsumer(config::setAllowPlayersChangePosition)
                .build());

        server.addEntry(entryBuilder.startLongField(Component.translatable("ontime.config.max_timer_seconds"),
                        config.getMaxTimerSeconds())
                .setDefaultValue(86400L)
                .setMin(1L)
                .setTooltip(Component.translatable("ontime.config.max_timer_seconds.tooltip"))
                .setSaveConsumer(config::setMaxTimerSeconds)
                .build());

        return builder.build();
    }
}