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
    private static final Map<UUID, Float> timerScale = new HashMap<>();

    public static boolean getTimerVisibility(UUID uuid) { return timerVisibility.getOrDefault(uuid, true); }
    public static void setTimerVisibility(UUID uuid, boolean visible) { timerVisibility.put(uuid, visible); save(); }

    public static boolean getTimerSilent(UUID uuid) { return timerSilent.getOrDefault(uuid, false); }
    public static void setTimerSilent(UUID uuid, boolean silent) { timerSilent.put(uuid, silent); save(); }

    public static String getTimerPosition(UUID uuid) { return timerPosition.getOrDefault(uuid, "BOSSBAR"); }
    public static void setTimerPosition(UUID uuid, String presetName) { timerPosition.put(uuid, presetName); save(); }

    public static float getTimerScale(UUID uuid) { return timerScale.getOrDefault(uuid, 1.0f); }
    public static void setTimerScale(UUID uuid, float scale) { timerScale.put(uuid, Math.max(0.1f, Math.min(5.0f, scale))); save(); }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            JsonObject root = new JsonObject();
            JsonObject vis = new JsonObject();
            JsonObject sil = new JsonObject();
            JsonObject pos = new JsonObject();
            JsonObject scl = new JsonObject();

            timerVisibility.forEach((k, v) -> vis.addProperty(k.toString(), v));
            timerSilent.forEach((k, v) -> sil.addProperty(k.toString(), v));
            timerPosition.forEach((k, v) -> pos.addProperty(k.toString(), v));
            timerScale.forEach((k, v) -> scl.addProperty(k.toString(), v));

            root.add("timerVisibility", vis);
            root.add("timerSilent", sil);
            root.add("timerPosition", pos);
            root.add("timerScale", scl);

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
            if (root.has("timerPosition")) {
                root.getAsJsonObject("timerPosition").entrySet().forEach(e -> {
                    try { timerPosition.put(UUID.fromString(e.getKey()), e.getValue().getAsString()); }
                    catch (IllegalArgumentException ignored) {}
                });
            }
            if (root.has("timerScale")) {
                root.getAsJsonObject("timerScale").entrySet().forEach(e -> {
                    try { timerScale.put(UUID.fromString(e.getKey()), e.getValue().getAsFloat()); }
                    catch (IllegalArgumentException ignored) {}
                });
            }
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