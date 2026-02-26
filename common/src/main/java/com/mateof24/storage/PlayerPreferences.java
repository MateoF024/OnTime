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

    public static boolean getTimerVisibility(UUID uuid) { return timerVisibility.getOrDefault(uuid, true); }
    public static void setTimerVisibility(UUID uuid, boolean visible) { timerVisibility.put(uuid, visible); save(); }

    public static boolean getTimerSilent(UUID uuid) { return timerSilent.getOrDefault(uuid, false); }
    public static void setTimerSilent(UUID uuid, boolean silent) { timerSilent.put(uuid, silent); save(); }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            JsonObject root = new JsonObject();
            JsonObject vis = new JsonObject();
            JsonObject sil = new JsonObject();

            timerVisibility.forEach((k, v) -> vis.addProperty(k.toString(), v));
            timerSilent.forEach((k, v) -> sil.addProperty(k.toString(), v));

            root.add("timerVisibility", vis);
            root.add("timerSilent", sil);

            try (FileWriter writer = new FileWriter(PREFS_FILE.toFile())) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            OnTimeConstants.LOGGER.error("Failed to save player preferences", e);
        }
    }

    public static void load() {
        if (!Files.exists(PREFS_FILE)) return;
        try (FileReader reader = new FileReader(PREFS_FILE.toFile())) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;
            loadBooleans(root, "timerVisibility", timerVisibility);
            loadBooleans(root, "timerSilent", timerSilent);
        } catch (IOException e) {
            OnTimeConstants.LOGGER.error("Failed to load player preferences", e);
        }
    }

    private static void loadBooleans(JsonObject root, String key, Map<UUID, Boolean> map) {
        if (!root.has(key)) return;
        root.getAsJsonObject(key).entrySet().forEach(e -> {
            try { map.put(UUID.fromString(e.getKey()), e.getValue().getAsBoolean()); }
            catch (IllegalArgumentException ignored) {}
        });
    }
}