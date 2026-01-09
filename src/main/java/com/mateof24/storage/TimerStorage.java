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

    public static void saveTimers(Map<String, Timer> timers, String activeTimerName) {
        try {
            Files.createDirectories(CONFIG_DIR);

            JsonObject root = new JsonObject();
            JsonArray timersArray = new JsonArray();

            for (Timer timer : timers.values()) {
                timersArray.add(timer.toJson());
            }

            root.add("timers", timersArray);

            if (activeTimerName != null && !activeTimerName.isEmpty()) {
                root.addProperty("activeTimer", activeTimerName);
            }

            try (FileWriter writer = new FileWriter(TIMERS_FILE.toFile())) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            OnTime.LOGGER.error("Failed to save timers", e);
        }
    }

    public static TimerLoadResult loadTimers() {
        Map<String, Timer> timers = new HashMap<>();
        String activeTimerName = null;

        if (!Files.exists(TIMERS_FILE)) {
            return new TimerLoadResult(timers, activeTimerName);
        }

        try (FileReader reader = new FileReader(TIMERS_FILE.toFile())) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);

            if (root != null) {
                if (root.has("timers")) {
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

                if (root.has("activeTimer") && !root.get("activeTimer").isJsonNull()) {
                    activeTimerName = root.get("activeTimer").getAsString();
                }
            }
        } catch (IOException e) {
            OnTime.LOGGER.error("Failed to load timers", e);
        }

        return new TimerLoadResult(timers, activeTimerName);
    }

    public static class TimerLoadResult {
        private final Map<String, Timer> timers;
        private final String activeTimerName;

        public TimerLoadResult(Map<String, Timer> timers, String activeTimerName) {
            this.timers = timers;
            this.activeTimerName = activeTimerName;
        }

        public Map<String, Timer> getTimers() {
            return timers;
        }

        public String getActiveTimerName() {
            return activeTimerName;
        }
    }
}