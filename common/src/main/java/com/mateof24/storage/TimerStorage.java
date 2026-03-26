package com.mateof24.storage;

import com.google.gson.*;
import com.mateof24.OnTimeConstants;
import com.mateof24.platform.Services;
import com.mateof24.timer.Timer;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class TimerStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = Services.PLATFORM.getConfigDir().resolve("ontime");
    private static final Path TIMERS_DIR = CONFIG_DIR.resolve("timers");
    private static final Path EXPORTS_DIR = CONFIG_DIR.resolve("exports");
    private static final Path LEGACY_FILE = CONFIG_DIR.resolve("timers.json");
    private static final Path ACTIVE_FILE = TIMERS_DIR.resolve("_active.json");

    public static void saveTimers(Map<String, Timer> timers, String activeTimerName) {
        try {
            Files.createDirectories(TIMERS_DIR);

            Set<String> expectedStems = new HashSet<>();
            for (String name : timers.keySet()) {
                expectedStems.add(sanitize(name));
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(TIMERS_DIR, "*.json")) {
                for (Path file : stream) {
                    String filename = file.getFileName().toString();
                    if (filename.startsWith("_")) continue;
                    String stem = filename.substring(0, filename.length() - 5);
                    if (!expectedStems.contains(stem)) {
                        Files.deleteIfExists(file);
                    }
                }
            } catch (IOException e) {
                OnTimeConstants.LOGGER.warn("Failed to clean stale timer files", e);
            }

            for (Timer timer : timers.values()) {
                saveTimer(timer);
            }

            saveActiveState(activeTimerName);
        } catch (IOException e) {
            OnTimeConstants.LOGGER.error("Failed to save timers", e);
        }
    }

    public static void saveTimer(Timer timer) {
        try {
            Files.createDirectories(TIMERS_DIR);
            Path file = TIMERS_DIR.resolve(sanitize(timer.getName()) + ".json");
            try (FileWriter writer = new FileWriter(file.toFile())) {
                GSON.toJson(timer.toJson(), writer);
            }
        } catch (IOException e) {
            OnTimeConstants.LOGGER.error("Failed to save timer '{}'", timer.getName(), e);
        }
    }

    public static void saveActiveState(String activeTimerName) {
        try {
            Files.createDirectories(TIMERS_DIR);
            JsonObject json = new JsonObject();
            if (activeTimerName != null && !activeTimerName.isEmpty()) {
                json.addProperty("activeTimer", activeTimerName);
            }
            try (FileWriter writer = new FileWriter(ACTIVE_FILE.toFile())) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            OnTimeConstants.LOGGER.error("Failed to save active timer state", e);
        }
    }

    public static TimerLoadResult loadTimers() {
        Map<String, Timer> timers = new HashMap<>();
        String activeTimerName = null;

        migrateLegacyIfNeeded();

        if (!Files.exists(TIMERS_DIR)) {
            return new TimerLoadResult(timers, activeTimerName);
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(TIMERS_DIR, "*.json")) {
            for (Path file : stream) {
                String filename = file.getFileName().toString();
                if (filename.startsWith("_")) continue;
                try (FileReader reader = new FileReader(file.toFile())) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json != null) {
                        Timer timer = Timer.fromJson(json);
                        timers.put(timer.getName(), timer);
                    }
                } catch (Exception e) {
                    OnTimeConstants.LOGGER.error("Failed to load timer from '{}'", filename, e);
                }
            }
        } catch (IOException e) {
            OnTimeConstants.LOGGER.error("Failed to read timers directory", e);
        }

        if (Files.exists(ACTIVE_FILE)) {
            try (FileReader reader = new FileReader(ACTIVE_FILE.toFile())) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json != null && json.has("activeTimer") && !json.get("activeTimer").isJsonNull()) {
                    activeTimerName = json.get("activeTimer").getAsString();
                }
            } catch (IOException e) {
                OnTimeConstants.LOGGER.error("Failed to load active timer state", e);
            }
        }

        return new TimerLoadResult(timers, activeTimerName);
    }

    private static void migrateLegacyIfNeeded() {
        if (!Files.exists(LEGACY_FILE)) return;

        boolean isEmpty = !Files.exists(TIMERS_DIR);
        if (!isEmpty) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(TIMERS_DIR, "*.json")) {
                isEmpty = !stream.iterator().hasNext();
            } catch (IOException e) {
                return;
            }
        }
        if (!isEmpty) return;

        OnTimeConstants.LOGGER.info("OnTime: migrating timers.json to per-file storage...");
        try (FileReader reader = new FileReader(LEGACY_FILE.toFile())) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;

            Map<String, Timer> timers = new HashMap<>();
            if (root.has("timers")) {
                for (JsonElement element : root.getAsJsonArray("timers")) {
                    try {
                        Timer timer = Timer.fromJson(element.getAsJsonObject());
                        timers.put(timer.getName(), timer);
                    } catch (Exception e) {
                        OnTimeConstants.LOGGER.error("Failed to migrate timer entry", e);
                    }
                }
            }
            String active = null;
            if (root.has("activeTimer") && !root.get("activeTimer").isJsonNull()) {
                active = root.get("activeTimer").getAsString();
            }
            saveTimers(timers, active);
            Files.move(LEGACY_FILE, CONFIG_DIR.resolve("timers.json.bak"), StandardCopyOption.REPLACE_EXISTING);
            OnTimeConstants.LOGGER.info("OnTime: migrated {} timer(s).", timers.size());
        } catch (IOException e) {
            OnTimeConstants.LOGGER.error("Failed to migrate legacy timers file", e);
        }
    }

    public static boolean exportTimer(String name, Timer timer) {
        try {
            Files.createDirectories(EXPORTS_DIR);
            Path dest = EXPORTS_DIR.resolve(sanitize(name) + ".json");
            try (FileWriter writer = new FileWriter(dest.toFile())) {
                GSON.toJson(timer.toJson(), writer);
            }
            return true;
        } catch (IOException e) {
            OnTimeConstants.LOGGER.error("Failed to export timer '{}'", name, e);
            return false;
        }
    }

    public static boolean exportFileExists(String filename) {
        return Files.exists(EXPORTS_DIR.resolve(sanitize(filename) + ".json"));
    }

    public static Timer importTimerFromExports(String filename) {
        Path source = EXPORTS_DIR.resolve(sanitize(filename) + ".json");
        if (!Files.exists(source)) return null;
        try (FileReader reader = new FileReader(source.toFile())) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) return null;
            return Timer.fromJson(json);
        } catch (Exception e) {
            OnTimeConstants.LOGGER.error("Failed to import timer from '{}'", filename, e);
            return null;
        }
    }

    public static List<String> getExportNames() {
        List<String> names = new ArrayList<>();
        if (!Files.exists(EXPORTS_DIR)) return names;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(EXPORTS_DIR, "*.json")) {
            for (Path file : stream) {
                names.add(file.getFileName().toString().replace(".json", ""));
            }
        } catch (IOException ignored) {}
        return names;
    }

    private static String sanitize(String name) {
        return name.replaceAll("[/\\\\:*?\"<>|]", "_");
    }

    public static class TimerLoadResult {
        private final Map<String, Timer> timers;
        private final String activeTimerName;

        public TimerLoadResult(Map<String, Timer> timers, String activeTimerName) {
            this.timers = timers;
            this.activeTimerName = activeTimerName;
        }

        public Map<String, Timer> getTimers() { return timers; }
        public String getActiveTimerName() { return activeTimerName; }
    }
}