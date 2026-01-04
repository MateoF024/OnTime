package com.mateof24.storage;

import com.google.gson.*;
import com.mateof24.OnTime;
import com.mateof24.timer.Timer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class TimerStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("ontime");
    private static final Path TIMERS_FILE = CONFIG_DIR.resolve("timers.json");

    // Save all timers to disk
    public static void saveTimers(Map<String, Timer> timers) {
        try {
            Files.createDirectories(CONFIG_DIR);

            JsonObject root = new JsonObject();
            JsonArray timersArray = new JsonArray();

            for (Timer timer : timers.values()) {
                timersArray.add(timer.toJson());
            }

            root.add("timers", timersArray);

            try (FileWriter writer = new FileWriter(TIMERS_FILE.toFile())) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            OnTime.LOGGER.error("Failed to save timers", e);
        }
    }

    // Load all timers from disk
    public static Map<String, Timer> loadTimers() {
        Map<String, Timer> timers = new HashMap<>();

        if (!Files.exists(TIMERS_FILE)) {
            return timers;
        }

        try (FileReader reader = new FileReader(TIMERS_FILE.toFile())) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);

            if (root != null && root.has("timers")) {
                JsonArray timersArray = root.getAsJsonArray("timers");

                for (JsonElement element : timersArray) {
                    try {
                        Timer timer = Timer.fromJson(element.getAsJsonObject());
                        timers.put(timer.getName(), timer);
                    } catch (Exception e) {
                        OnTime.LOGGER.error("Failed to load timer", e);
                    }
                }
            }
        } catch (IOException e) {
            OnTime.LOGGER.error("Failed to load timers", e);
        }

        return timers;
    }
}