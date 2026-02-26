package com.mateof24.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfigScreen {

    // Crear pantalla de configuración in-game
    public static Screen createConfigScreen(Screen parent) {
        ModConfig config = ModConfig.getInstance();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("ontime.config.title"));

        builder.setSavingRunnable(config::save);

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // Categoria "Display" (Posición y Escala)
        ConfigCategory display = builder.getOrCreateCategory(Component.translatable("ontime.config.category.display"));

        display.addEntry(entryBuilder.startSelector(
                        Component.translatable("ontime.config.position_preset"),
                        TimerPositionPreset.values(),
                        config.getPositionPreset())
                .setDefaultValue(TimerPositionPreset.BOSSBAR)
                .setNameProvider(preset -> Component.literal(preset.getDisplayName()))
                .setTooltip(Component.translatable("ontime.config.position_preset.tooltip"))
                .setSaveConsumer(preset -> {
                    Minecraft mc = Minecraft.getInstance();
                    int screenWidth = mc.getWindow().getGuiScaledWidth();
                    int screenHeight = mc.getWindow().getGuiScaledHeight();

                    String timeText = "00:00";
                    int textWidth = (int) (mc.font.width(timeText) * config.getTimerScale());
                    int textHeight = (int) (mc.font.lineHeight * config.getTimerScale());

                    config.applyPreset(preset, screenWidth, screenHeight, textWidth, textHeight);
                })
                .build());
        // Muestro opciones de X, Y solo si el preset se encuentra en CUSTOM
        if (config.getPositionPreset() == TimerPositionPreset.CUSTOM) {
        display.addEntry(entryBuilder.startIntField(Component.translatable("ontime.config.timer_x"), config.getTimerX())
                .setDefaultValue(-1)
                .setTooltip(Component.translatable(
                        config.getPositionPreset() == TimerPositionPreset.CUSTOM
                                ? "ontime.config.timer_x.tooltip"
                                : "ontime.config.timer_x.disabled"
                ))
                .setSaveConsumer(x -> {

                        config.setTimerX(x);

                })
                .build());
                }

        if (config.getPositionPreset() == TimerPositionPreset.CUSTOM) {
        display.addEntry(entryBuilder.startIntField(Component.translatable("ontime.config.timer_y"), config.getTimerY())
                .setDefaultValue(4)
                .setMin(0)
                .setTooltip(Component.translatable(
                        config.getPositionPreset() == TimerPositionPreset.CUSTOM
                                ? "ontime.config.timer_y.tooltip"
                                : "ontime.config.timer_y.disabled"
                ))
                .setSaveConsumer(y -> {
                        config.setTimerY(y);

                })
                .build());
                        }

        display.addEntry(entryBuilder.startFloatField(Component.translatable("ontime.config.timer_scale"), config.getTimerScale())
                .setDefaultValue(1.0f)
                .setMin(0.1f)
                .setMax(5.0f)
                .setTooltip(Component.translatable("ontime.config.timer_scale.tooltip"))
                .setSaveConsumer(config::setTimerScale)
                .build());

        // Categoria de "Colors"
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

        // Categoria de "Server"
        ConfigCategory server = builder.getOrCreateCategory(Component.translatable("ontime.config.category.server"));

        server.addEntry(entryBuilder.startLongField(Component.translatable("ontime.config.max_timer_seconds"),
                        config.getMaxTimerSeconds())
                .setDefaultValue(86400L)
                .setMin(1L)
                .setTooltip(Component.translatable("ontime.config.max_timer_seconds.tooltip"))
                .setSaveConsumer(config::setMaxTimerSeconds)
                .build());

        server.addEntry(entryBuilder.startStrField(Component.translatable("ontime.config.timer_sound_id"),
                        config.getTimerSoundId())
                .setDefaultValue("minecraft:block.note_block.hat")
                .setTooltip(Component.translatable("ontime.config.timer_sound_id.tooltip"))
                .setSaveConsumer(config::setTimerSoundId)
                .build());

        server.addEntry(entryBuilder.startFloatField(Component.translatable("ontime.config.timer_sound_volume"),
                        config.getTimerSoundVolume())
                .setDefaultValue(1.0f)
                .setMin(0.0f)
                .setTooltip(Component.translatable("ontime.config.timer_sound_volume.tooltip"))
                .setSaveConsumer(config::setTimerSoundVolume)
                .build());

        server.addEntry(entryBuilder.startFloatField(Component.translatable("ontime.config.timer_sound_pitch"),
                        config.getTimerSoundPitch())
                .setDefaultValue(2.0f)
                .setMin(0.0f)
                .setTooltip(Component.translatable("ontime.config.timer_sound_pitch.tooltip"))
                .setSaveConsumer(config::setTimerSoundPitch)
                .build());

        return builder.build();
    }
}