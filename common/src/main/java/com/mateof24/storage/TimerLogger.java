package com.mateof24.storage;

import com.google.gson.*;
import com.mateof24.OnTimeConstants;
import com.mateof24.platform.Services;
import com.mateof24.timer.Timer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TimerLogger {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = Services.PLATFORM.getConfigDir().resolve("ontime");
    private static final Path LOG_FILE = CONFIG_DIR.resolve("history.json");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void logFinish(Timer timer) {
        try {
            Files.createDirectories(CONFIG_DIR);

            JsonArray history = new JsonArray();

            if (Files.exists(LOG_FILE)) {
                try (FileReader reader = new FileReader(LOG_FILE.toFile())) {
                    JsonElement element = GSON.fromJson(reader, JsonElement.class);
                    if (element != null && element.isJsonArray()) {
                        history = element.getAsJsonArray();
                    }
                } catch (Exception e) {
                    OnTimeConstants.LOGGER.warn("Could not read history file, starting fresh", e);
                }
            }

            JsonObject entry = new JsonObject();
            entry.addProperty("timestamp", LocalDateTime.now().format(FORMATTER));
            entry.addProperty("name", timer.getName());
            entry.addProperty("duration", formatSeconds(timer.getTargetTicks() / 20L));
            entry.addProperty("mode", timer.isCountUp() ? "count-up" : "countdown");
            entry.addProperty("command", timer.getCommand() != null ? timer.getCommand() : "");
            if (timer.isRepeat()) {
                entry.addProperty("repeatsDone", timer.getRepeatsDone());
            }

            history.add(entry);

            try (FileWriter writer = new FileWriter(LOG_FILE.toFile())) {
                GSON.toJson(history, writer);
            }
        } catch (IOException e) {
            OnTimeConstants.LOGGER.error("Failed to write timer history", e);
        }
    }

    private static String formatSeconds(long total) {
        long h = total / 3600, m = (total % 3600) / 60, s = total % 60;
        return h > 0 ? String.format("%02d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }
}