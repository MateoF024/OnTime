package com.mateof24.storage;

import com.google.gson.*;
import com.mateof24.OnTimeConstants;
import com.mateof24.platform.Services;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerPreferences {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = Services.PLATFORM.getConfigDir().resolve("ontime");
    private static final Path PREFS_FILE = CONFIG_DIR.resolve("player_preferences.json");

    private static final Map<UUID, Boolean> timerVisibility = new HashMap<>();
    private static final Map<UUID, Boolean> timerSilent = new HashMap<>();
    private static final Map<UUID, String> timerPosition = new HashMap<>();

    public static void setTimerVisibility(UUID playerUUID, boolean visible) {
        timerVisibility.put(playerUUID, visible);
        save();
    }

    public static boolean getTimerVisibility(UUID playerUUID) {
        return timerVisibility.getOrDefault(playerUUID, true);
    }

    public static void setTimerSilent(UUID playerUUID, boolean silent) {
        timerSilent.put(playerUUID, silent);
        save();
    }

    public static boolean getTimerSilent(UUID playerUUID) {
        return timerSilent.getOrDefault(playerUUID, false);
    }

    public static void setTimerPosition(UUID playerUUID, String presetName) {
        timerPosition.put(playerUUID, presetName);
        save();
    }

    public static String getTimerPosition(UUID playerUUID) {
        return timerPosition.getOrDefault(playerUUID, "BOSSBAR");
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            JsonObject root = new JsonObject();
            JsonObject visibility = new JsonObject();
            JsonObject silent = new JsonObject();
            JsonObject position = new JsonObject();

            for (Map.Entry<UUID, Boolean> entry : timerVisibility.entrySet()) {
                visibility.addProperty(entry.getKey().toString(), entry.getValue());
            }
            for (Map.Entry<UUID, Boolean> entry : timerSilent.entrySet()) {
                silent.addProperty(entry.getKey().toString(), entry.getValue());
            }
            for (Map.Entry<UUID, String> entry : timerPosition.entrySet()) {
                position.addProperty(entry.getKey().toString(), entry.getValue());
            }

            root.add("timerPosition", position);

            root.add("timerVisibility", visibility);
            root.add("timerSilent", silent);
            try (FileWriter writer = new FileWriter(PREFS_FILE.toFile())) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            OnTimeConstants.LOGGER.error("Failed to save player preferences", e);
        }
    }

    public static void load() {
        if (!Files.exists(PREFS_FILE)) {
            return;
        }

        try (FileReader reader = new FileReader(PREFS_FILE.toFile())) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);

            if (root != null && root.has("timerVisibility")) {
                JsonObject visibility = root.getAsJsonObject("timerVisibility");

                for (Map.Entry<String, JsonElement> entry : visibility.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        boolean visible = entry.getValue().getAsBoolean();
                        timerVisibility.put(uuid, visible);
                    } catch (IllegalArgumentException e) {
                        OnTimeConstants.LOGGER.warn("Invalid UUID in player preferences: {}", entry.getKey());
                    }
                }
            }
            if (root != null && root.has("timerSilent")) {
                JsonObject silent = root.getAsJsonObject("timerSilent");

                for (Map.Entry<String, JsonElement> entry : silent.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        boolean silentValue = entry.getValue().getAsBoolean();
                        timerSilent.put(uuid, silentValue);
                    } catch (IllegalArgumentException e) {
                        OnTimeConstants.LOGGER.warn("Invalid UUID in player silent preferences: {}", entry.getKey());
                    }
                }
            }
            if (root != null && root.has("timerPosition")) {
                JsonObject position = root.getAsJsonObject("timerPosition");

                for (Map.Entry<String, JsonElement> entry : position.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        String presetName = entry.getValue().getAsString();
                        timerPosition.put(uuid, presetName);
                    } catch (IllegalArgumentException e) {
                        OnTimeConstants.LOGGER.warn("Invalid UUID in player position preferences: {}", entry.getKey());
                    }
                }
            }
        } catch (IOException e) {
            OnTimeConstants.LOGGER.error("Failed to load player preferences", e);
        }
    }
}