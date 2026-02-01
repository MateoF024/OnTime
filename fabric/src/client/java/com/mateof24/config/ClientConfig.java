package com.mateof24.config;

import com.google.gson.*;
import com.mateof24.OnTime;
import net.fabricmc.loader.api.FabricLoader;
import com.mateof24.config.TimerPositionPreset;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("ontime");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("client-config.json");

    private static ClientConfig INSTANCE;

    private int timerX = -1;
    private int timerY = 4;
    private float timerScale = 1.0f;
    private TimerPositionPreset positionPreset = TimerPositionPreset.BOSSBAR;

    private ClientConfig() {}

    public static ClientConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ClientConfig();
            INSTANCE.load();
        }
        return INSTANCE;
    }

    public void load() {
        if (!Files.exists(CONFIG_FILE)) {
            save();
            return;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE.toFile())) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root != null) {
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
            }
        } catch (Exception e) {
            OnTime.LOGGER.error("Failed to load client config", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            JsonObject root = new JsonObject();

            root.addProperty("timerX", timerX);
            root.addProperty("timerY", timerY);
            root.addProperty("timerScale", timerScale);
            root.addProperty("positionPreset", positionPreset.name());

            try (FileWriter writer = new FileWriter(CONFIG_FILE.toFile())) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            OnTime.LOGGER.error("Failed to save client config", e);
        }
    }

    public int getTimerX() { return timerX; }
    public int getTimerY() { return timerY; }
    public float getTimerScale() { return timerScale; }
    public TimerPositionPreset getPositionPreset() {
        return positionPreset;
    }

    public void setPositionPreset(TimerPositionPreset preset) {
        this.positionPreset = preset;

        // Si el preset no es CUSTOM, no guardamos x, y específicos
        // ya que se calcularán dinámicamente
        save();
    }

    /**
     * Aplica un preset y actualiza las coordenadas
     * @param preset El preset a aplicar
     * @param screenWidth Ancho actual de la pantalla
     * @param screenHeight Alto actual de la pantalla
     * @param timerWidth Ancho del timer
     * @param timerHeight Alto del timer
     */
    /**
     * Aplica un preset y actualiza las coordenadas
     */
    public void applyPreset(TimerPositionPreset preset, int screenWidth, int screenHeight,
                            int timerWidth, int timerHeight) {
        this.positionPreset = preset;

        // Siempre recalcular las coordenadas según el preset
        // Esto asegura que se guarden las posiciones correctas
        this.timerX = preset.calculateX(screenWidth, timerWidth, this.timerX);
        this.timerY = preset.calculateY(screenHeight, timerHeight, this.timerY);

        save();
    }

    /**
     * Actualiza las coordenadas manualmente (cambia a CUSTOM)
     */
    public void setCustomPosition(int x, int y) {
        this.positionPreset = TimerPositionPreset.CUSTOM;
        this.timerX = x;
        this.timerY = Math.max(0, y);
        save();
    }

    /**
     * Establece X sin cambiar el preset (usado por ConfigScreen)
     */
    public void setTimerX(int x) {
        this.timerX = x;
        save();
    }

    /**
     * Establece Y sin cambiar el preset (usado por ConfigScreen)
     */
    public void setTimerY(int y) {
        this.timerY = Math.max(0, y);
        save();
    }

    /**
     * Establece X Y cambiando a CUSTOM (usado cuando el usuario mueve manualmente)
     */
    public void setCustomPositionX(int x) {
        this.positionPreset = TimerPositionPreset.CUSTOM;
        this.timerX = x;
        save();
    }

    /**
     * Establece Y cambiando a CUSTOM (usado cuando el usuario mueve manualmente)
     */
    public void setCustomPositionY(int y) {
        this.positionPreset = TimerPositionPreset.CUSTOM;
        this.timerY = Math.max(0, y);
        save();
    }

    public void setTimerScale(float scale) {
        this.timerScale = Math.max(0.1f, Math.min(5.0f, scale));
        save();
    }
}