package com.mateof24.config;

import com.google.gson.*;
import com.mateof24.OnTimeConstants;
import com.mateof24.platform.Services;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    // Creación de archivo de configuración principal.
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = Services.PLATFORM.getConfigDir().resolve("ontime");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");

    private static ModConfig INSTANCE;

    private ModConfig() {}

    public static ModConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ModConfig();
            INSTANCE.load();
        }
        return INSTANCE;
    }

    // Variables Configurables (Valores Predeterminados)

    private int timerX = -1;
    private int timerY = 4;
    private TimerPositionPreset positionPreset = TimerPositionPreset.BOSSBAR;
    private float timerScale = 1.0f;
    private long maxTimerSeconds = 86400;
    private int colorHigh = 0xFFFFFF;
    private int colorMid = 0xFFFF00;
    private int colorLow = 0xFF0000;
    private int thresholdMid = 30;
    private int thresholdLow = 10;
    private String timerSoundId = "minecraft:block.note_block.hat";
    private float timerSoundVolume = 1.0f;
    private float timerSoundPitch = 2.0f;


    // Función para cargar parámetros
    public void load() {
        if (!Files.exists(CONFIG_FILE)) {
            save();
            return;
        }
        try (FileReader reader = new FileReader(CONFIG_FILE.toFile())) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;
            if (root.has("timerX")) {
                timerX = root.get("timerX").getAsInt();
            }
            if (root.has("timerY")) {
                timerY = root.get("timerY").getAsInt();
                timerY = Math.max(0, timerY);
            }
            if (root.has("timerScale")) {
                timerScale = root.get("timerScale").getAsFloat();
                timerScale = Math.max(0.1f, Math.min(5.0f, timerScale));
            }
            if (root.has("positionPreset")) {
                String presetName = root.get("positionPreset").getAsString();
                positionPreset = TimerPositionPreset.fromString(presetName);
            }
            if (root.has("maxTimerSeconds")) maxTimerSeconds = root.get("maxTimerSeconds").getAsLong();
            if (root.has("colorHigh")) colorHigh = parseColor(root.get("colorHigh").getAsString());
            if (root.has("colorMid")) colorMid = parseColor(root.get("colorMid").getAsString());
            if (root.has("colorLow")) colorLow = parseColor(root.get("colorLow").getAsString());
            if (root.has("thresholdMid")) thresholdMid = root.get("thresholdMid").getAsInt();
            if (root.has("thresholdLow")) thresholdLow = root.get("thresholdLow").getAsInt();
            if (root.has("timerSoundId")) timerSoundId = root.get("timerSoundId").getAsString();
            if (root.has("timerSoundVolume")) timerSoundVolume = root.get("timerSoundVolume").getAsFloat();
            if (root.has("timerSoundPitch")) timerSoundPitch = root.get("timerSoundPitch").getAsFloat();
        } catch (IOException e) {
            OnTimeConstants.LOGGER.error("Failed to load config", e);
        }
    }

    // Función para guardar parámetros
    public void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            JsonObject root = new JsonObject();
            root.addProperty("timerX", timerX);
            root.addProperty("timerY", timerY);
            root.addProperty("timerScale", timerScale);
            root.addProperty("positionPreset", positionPreset.name());
            root.addProperty("maxTimerSeconds", maxTimerSeconds);
            root.addProperty("colorHigh", String.format("#%06X", colorHigh));
            root.addProperty("colorMid", String.format("#%06X", colorMid));
            root.addProperty("colorLow", String.format("#%06X", colorLow));
            root.addProperty("thresholdMid", thresholdMid);
            root.addProperty("thresholdLow", thresholdLow);
            root.addProperty("timerSoundId", timerSoundId);
            root.addProperty("timerSoundVolume", timerSoundVolume);
            root.addProperty("timerSoundPitch", timerSoundPitch);
            try (FileWriter writer = new FileWriter(CONFIG_FILE.toFile())) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            OnTimeConstants.LOGGER.error("Failed to save config", e);
        }
    }


    // Métodos para obtener y establecer presets de posición.
    public TimerPositionPreset getPositionPreset() {
        return positionPreset;
    }
    public void setPositionPreset(TimerPositionPreset preset) {
        this.positionPreset = preset;

        save();
    }
    public void applyPreset(TimerPositionPreset preset, int screenWidth, int screenHeight,
                            int timerWidth, int timerHeight) {
        this.positionPreset = preset;


        this.timerX = preset.calculateX(screenWidth, timerWidth, this.timerX);
        this.timerY = preset.calculateY(screenHeight, timerHeight, this.timerY);

        save();
    }

    // Métodos para obtener y establecer posición

    public int getTimerX() { return timerX; }
    public int getTimerY() { return timerY; }
    public void setTimerX(int x) {
        this.timerX = x;
        save();
    }
    public void setTimerY(int y) {
        this.timerY = Math.max(0, y);
        save();
    }

    public void setCustomPosition(int x, int y) {
        this.positionPreset = TimerPositionPreset.CUSTOM;
        this.timerX = x;
        this.timerY = Math.max(0, y);
        save();
    }
    public void setCustomPositionX(int x) {
        this.positionPreset = TimerPositionPreset.CUSTOM;
        this.timerX = x;
        save();
    }
    public void setCustomPositionY(int y) {
        this.positionPreset = TimerPositionPreset.CUSTOM;
        this.timerY = Math.max(0, y);
        save();
    }

    // Métodos para obtener y establecer escalas.

    public float getTimerScale() { return timerScale; }
    public void setTimerScale(float scale) {
        this.timerScale = Math.max(0.1f, Math.min(5.0f, scale));
        save();
    }


    // Métodos para obtener y establecer tiempos.

    public long getMaxTimerSeconds() { return maxTimerSeconds; }
    public void setMaxTimerSeconds(long seconds) { this.maxTimerSeconds = Math.max(1, seconds); save(); }

    // Métodos para obtener y establecer colores

    private int parseColor(String hex) {
        try { return Integer.parseInt(hex.replace("#", ""), 16); }
        catch (NumberFormatException e) { return 0xFFFFFF; }
    }

    public int getColorForPercentage(float percentage) {
        if (percentage >= thresholdMid) {
            return colorHigh;
        } else if (percentage >= thresholdLow) {
            return colorMid;
        } else {
            return colorLow;
        }
    }

    public int getColorHigh() { return colorHigh; }
    public void setColorHigh(int color) { this.colorHigh = color; save(); }

    public int getColorMid() { return colorMid; }
    public void setColorMid(int color) { this.colorMid = color; save(); }

    public int getColorLow() { return colorLow; }
    public void setColorLow(int color) { this.colorLow = color; save(); }

    public int getThresholdMid() { return thresholdMid; }
    public void setThresholdMid(int threshold) { this.thresholdMid = threshold; save(); }

    public int getThresholdLow() { return thresholdLow; }
    public void setThresholdLow(int threshold) { this.thresholdLow = threshold; save(); }

    // Métodos para obtener y establecer sonido, volumen y pitch

    public String getTimerSoundId() { return timerSoundId; }
    public void setTimerSoundId(String soundId) { this.timerSoundId = soundId; save(); }

    public float getTimerSoundVolume() { return timerSoundVolume; }
    public void setTimerSoundVolume(float volume) { this.timerSoundVolume = volume; save(); }

    public float getTimerSoundPitch() { return timerSoundPitch; }
    public void setTimerSoundPitch(float pitch) { this.timerSoundPitch = pitch; save(); }
}