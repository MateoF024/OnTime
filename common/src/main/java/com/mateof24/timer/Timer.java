package com.mateof24.timer;

import com.google.gson.JsonObject;

public class Timer {
    private final String name;
    private long currentTicks;
    private final long targetTicks;
    private final boolean countUp;
    private boolean running;
    private String command;
    private boolean silent;
    private boolean wasRunningBeforeShutdown;

    public Timer(String name, int hours, int minutes, int seconds, boolean countUp) {
        this.name = name;
        this.targetTicks = (hours * 3600L + minutes * 60L + seconds) * 20L;
        this.countUp = countUp;
        this.currentTicks = countUp ? 0 : this.targetTicks;
        this.running = false;
        this.command = com.mateof24.command.PlaceholderSystem.DEFAULT_COMMAND;
        this.silent = false;
        this.wasRunningBeforeShutdown = false;
    }

    public boolean tick() {
        if (!running) return false;

        if (countUp) {
            currentTicks++;
            if (currentTicks >= targetTicks) {
                reset();
                return true;
            }
        } else {
            currentTicks--;
            if (currentTicks <= 0) {
                reset();
                return true;
            }
        }
        return false;
    }

    public void reset() {
        currentTicks = countUp ? 0 : targetTicks;
        running = false;
    }

    public void setTime(int hours, int minutes, int seconds) {
        currentTicks = (hours * 3600L + minutes * 60L + seconds) * 20L;
    }

    public void addTime(int hours, int minutes, int seconds) {
        long additionalTicks = (hours * 3600L + minutes * 60L + seconds) * 20L;
        currentTicks += additionalTicks;

        if (countUp && currentTicks > targetTicks) {
            currentTicks = targetTicks;
        } else if (!countUp && currentTicks < 0) {
            currentTicks = 0;
        }
    }

    public String getFormattedTime() {
        long totalSeconds = currentTicks / 20L;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("currentTicks", currentTicks);
        json.addProperty("targetTicks", targetTicks);
        json.addProperty("countUp", countUp);
        json.addProperty("running", running);
        json.addProperty("command", command != null ? command : "");
        json.addProperty("silent", silent);
        json.addProperty("wasRunningBeforeShutdown", wasRunningBeforeShutdown);
        return json;
    }

    public static Timer fromJson(JsonObject json) {
        String name = json.get("name").getAsString();
        long targetTicks = json.get("targetTicks").getAsLong();
        boolean countUp = json.get("countUp").getAsBoolean();

        int totalSeconds = (int) (targetTicks / 20L);
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        Timer timer = new Timer(name, hours, minutes, seconds, countUp);
        timer.currentTicks = json.get("currentTicks").getAsLong();
        timer.running = json.get("running").getAsBoolean();
        timer.command = json.has("command") && !json.get("command").isJsonNull()
                ? json.get("command").getAsString()
                : com.mateof24.command.PlaceholderSystem.DEFAULT_COMMAND;
        timer.silent = json.has("silent") && json.get("silent").getAsBoolean();
        timer.wasRunningBeforeShutdown = json.has("wasRunningBeforeShutdown")
                && json.get("wasRunningBeforeShutdown").getAsBoolean();

        return timer;
    }

    public String getName() { return name; }
    public long getCurrentTicks() { return currentTicks; }
    public long getTargetTicks() { return targetTicks; }
    public boolean isCountUp() { return countUp; }
    public boolean isRunning() { return running; }
    public void setRunning(boolean running) { this.running = running; }
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command != null ? command : ""; }
    public void setCurrentTicks(long ticks) { this.currentTicks = ticks; }
    public boolean isSilent() { return silent; }
    public void setSilent(boolean silent) { this.silent = silent; }
    public boolean wasRunningBeforeShutdown() { return wasRunningBeforeShutdown; }
    public void setWasRunningBeforeShutdown(boolean was) { this.wasRunningBeforeShutdown = was; }
}