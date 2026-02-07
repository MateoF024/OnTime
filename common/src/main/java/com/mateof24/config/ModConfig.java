package com.mateof24.config;

import com.google.gson.*;
import com.mateof24.OnTimeConstants;
import com.mateof24.platform.Services;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
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
                if (root.has("allowPlayersUseHide")) {
                    allowPlayersUseHide = root.get("allowPlayersUseHide").getAsBoolean();
                }
                if (root.has("allowPlayersUseList")) {
                    allowPlayersUseList = root.get("allowPlayersUseList").getAsBoolean();
                }
                if (root.has("allowPlayersUseSilent")) {
                    allowPlayersUseSilent = root.get("allowPlayersUseSilent").getAsBoolean();
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
                if (root.has("allowPlayersChangePosition")) {
                    allowPlayersChangePosition = root.get("allowPlayersChangePosition").getAsBoolean();
                }
                if (root.has("allowPlayersChangeSound")) {
                    allowPlayersChangeSound = root.get("allowPlayersChangeSound").getAsBoolean();
                }
                if (root.has("timerSoundId")) {
                    timerSoundId = root.get("timerSoundId").getAsString();
                }
                if (root.has("timerSoundVolume")) {
                    timerSoundVolume = root.get("timerSoundVolume").getAsFloat();
                    timerSoundVolume = Math.max(0.0f, Math.min(1.0f, timerSoundVolume));
                }
                if (root.has("timerSoundPitch")) {
                    timerSoundPitch = root.get("timerSoundPitch").getAsFloat();
                    timerSoundPitch = Math.max(0.5f, Math.min(2.0f, timerSoundPitch));
                }
            }
        } catch (IOException e) {
            OnTimeConstants.LOGGER.error("Failed to load config", e);
        } catch (Exception e) {
            OnTimeConstants.LOGGER.error("Failed to parse config", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            JsonObject root = new JsonObject();

            root.addProperty("requiredPermissionLevel", requiredPermissionLevel);
            root.addProperty("allowPlayersUseHide", allowPlayersUseHide);
            root.addProperty("allowPlayersUseList", allowPlayersUseList);
            root.addProperty("allowPlayersUseSilent", allowPlayersUseSilent);
            root.addProperty("allowPlayersChangePosition", allowPlayersChangePosition);
            root.addProperty("allowPlayersChangeSound", allowPlayersChangeSound);
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

    private int requiredPermissionLevel = 2;
    private boolean allowPlayersUseHide = false;
    private boolean allowPlayersUseList = false;
    private boolean allowPlayersUseSilent = false;
    private boolean allowPlayersChangePosition = false;
    private boolean allowPlayersChangeSound = false;
    private String timerSoundId = "minecraft:block.note_block.hat";
    private float timerSoundVolume = 0.75f;
    private float timerSoundPitch = 2.0f;

    public boolean getAllowPlayersUseHide() {
        return allowPlayersUseHide;
    }

    public void setAllowPlayersUseHide(boolean allow) {
        this.allowPlayersUseHide = allow;
        save();
    }

    public boolean getAllowPlayersUseList() {
        return allowPlayersUseList;
    }

    public void setAllowPlayersUseList(boolean allow) {
        this.allowPlayersUseList = allow;
        save();
    }

    public boolean getAllowPlayersUseSilent() {
        return allowPlayersUseSilent;
    }

    public void setAllowPlayersUseSilent(boolean allow) {
        this.allowPlayersUseSilent = allow;
        save();
    }

    public boolean getAllowPlayersChangePosition() {
        return allowPlayersChangePosition;
    }

    public void setAllowPlayersChangePosition(boolean allow) {
        this.allowPlayersChangePosition = allow;
        save();
    }

    public boolean getAllowPlayersChangeSound() {
        return allowPlayersChangeSound;
    }

    public void setAllowPlayersChangeSound(boolean allow) {
        this.allowPlayersChangeSound = allow;
        save();
    }

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
            OnTimeConstants.LOGGER.warn("Invalid color format: {}, using default", colorStr);
            return 0xFFFFFF;
        }
    }

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

    public String getTimerSoundId() {
        return timerSoundId;
    }

    public void setTimerSoundId(String soundId) {
        this.timerSoundId = soundId != null ? soundId : "minecraft:block.note_block.hat";
        save();
    }

    public float getTimerSoundVolume() {
        return timerSoundVolume;
    }

    public void setTimerSoundVolume(float volume) {
        this.timerSoundVolume = Math.max(0.0f, Math.min(1.0f, volume));
        save();
    }

    public float getTimerSoundPitch() {
        return timerSoundPitch;
    }

    public void setTimerSoundPitch(float pitch) {
        this.timerSoundPitch = Math.max(0.5f, Math.min(2.0f, pitch));
        save();
    }
}