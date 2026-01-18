package com.mateof24.config;

import com.google.gson.*;
import com.mateof24.OnTime;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("ontime");
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

    public void load() {
        if (!Files.exists(CONFIG_FILE)) {
            save();
            return;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE.toFile())) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root != null) {
                if (root.has("requiredPermissionLevel")) {
                    requiredPermissionLevel = root.get("requiredPermissionLevel").getAsInt();
                    requiredPermissionLevel = Math.max(0, Math.min(4, requiredPermissionLevel));
                }
                if (root.has("maxTimerSeconds")) {
                    maxTimerSeconds = root.get("maxTimerSeconds").getAsLong();
                    maxTimerSeconds = Math.max(1, maxTimerSeconds);
                }
                if (root.has("colorHigh")) {
                    colorHigh = parseColor(root.get("colorHigh").getAsString());
                }
                if (root.has("colorMid")) {
                    colorMid = parseColor(root.get("colorMid").getAsString());
                }
                if (root.has("colorLow")) {
                    colorLow = parseColor(root.get("colorLow").getAsString());
                }
                if (root.has("thresholdMid")) {
                    thresholdMid = root.get("thresholdMid").getAsInt();
                    thresholdMid = Math.max(0, Math.min(100, thresholdMid));
                }
                if (root.has("thresholdLow")) {
                    thresholdLow = root.get("thresholdLow").getAsInt();
                    thresholdLow = Math.max(0, Math.min(100, thresholdLow));
                }
            }
        } catch (IOException e) {
            OnTime.LOGGER.error("Failed to load config", e);
        } catch (Exception e) {
            OnTime.LOGGER.error("Failed to parse config", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            JsonObject root = new JsonObject();

            root.addProperty("requiredPermissionLevel", requiredPermissionLevel);
            root.addProperty("maxTimerSeconds", maxTimerSeconds);
            root.addProperty("colorHigh", String.format("#%06X", colorHigh));
            root.addProperty("colorMid", String.format("#%06X", colorMid));
            root.addProperty("colorLow", String.format("#%06X", colorLow));
            root.addProperty("thresholdMid", thresholdMid);
            root.addProperty("thresholdLow", thresholdLow);

            try (FileWriter writer = new FileWriter(CONFIG_FILE.toFile())) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            OnTime.LOGGER.error("Failed to save config", e);
        }
    }

    private int requiredPermissionLevel = 2;

    public int getRequiredPermissionLevel() {
        return requiredPermissionLevel;
    }

    public void setRequiredPermissionLevel(int level) {
        this.requiredPermissionLevel = Math.max(0, Math.min(4, level));
        save();
    }

    private long maxTimerSeconds = 86400;

    public long getMaxTimerSeconds() {
        return maxTimerSeconds;
    }

    public void setMaxTimerSeconds(long seconds) {
        this.maxTimerSeconds = Math.max(1, seconds);
        save();
    }

    public long getMaxTimerTicks() {
        return maxTimerSeconds * 20L;
    }

    private int colorHigh = 0xFFFFFF;
    private int colorMid = 0xFFFF00;
    private int colorLow = 0xFF0000;
    private int thresholdMid = 30;
    private int thresholdLow = 10;

    public int getColorHigh() { return colorHigh; }
    public int getColorMid() { return colorMid; }
    public int getColorLow() { return colorLow; }
    public int getThresholdMid() { return thresholdMid; }
    public int getThresholdLow() { return thresholdLow; }

    public void setColorHigh(int color) {
        this.colorHigh = color;
        save();
    }

    public void setColorMid(int color) {
        this.colorMid = color;
        save();
    }

    public void setColorLow(int color) {
        this.colorLow = color;
        save();
    }

    public void setThresholdMid(int threshold) {
        this.thresholdMid = Math.max(0, Math.min(100, threshold));
        save();
    }

    public void setThresholdLow(int threshold) {
        this.thresholdLow = Math.max(0, Math.min(100, threshold));
        save();
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


    private int parseColor(String colorStr) {
        try {
            if (colorStr.startsWith("#")) {
                return Integer.parseInt(colorStr.substring(1), 16);
            }
            return Integer.parseInt(colorStr, 16);
        } catch (NumberFormatException e) {
            OnTime.LOGGER.warn("Invalid color format: {}, using default", colorStr);
            return 0xFFFFFF;
        }
    }

}