package com.mateof24.config;

import com.google.gson.*;
import com.mateof24.OnTimeConstants;
import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("ontime");
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
            OnTimeConstants.LOGGER.error("Failed to load client config", e);
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
            OnTimeConstants.LOGGER.error("Failed to save client config", e);
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
        save();
    }

    public void applyPreset(TimerPositionPreset preset, int screenWidth, int screenHeight,
                            int timerWidth, int timerHeight) {
        this.positionPreset = preset;
        this.timerX = preset.calculateX(screenWidth, timerWidth, this.timerX);
        this.timerY = preset.calculateY(screenHeight, timerHeight, this.timerY);
        save();
    }

    public void setCustomPosition(int x, int y) {
        this.positionPreset = TimerPositionPreset.CUSTOM;
        this.timerX = x;
        this.timerY = Math.max(0, y);
        save();
    }

    public void setTimerX(int x) {
        this.timerX = x;
        save();
    }

    public void setTimerY(int y) {
        this.timerY = Math.max(0, y);
        save();
    }

    public void setTimerScale(float scale) {
        this.timerScale = Math.max(0.1f, Math.min(5.0f, scale));
        save();
    }
}