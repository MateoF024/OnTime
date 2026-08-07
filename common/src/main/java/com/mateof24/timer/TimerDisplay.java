package com.mateof24.timer;

import com.google.gson.JsonObject;
import com.mateof24.config.ModConfig;

/**
 * How one timer looks and sounds — its own copy, not a view of the defaults.
 *
 * <p>Every field here used to live in {@link ModConfig} and be read at draw
 * time, which made the config a live global: editing a colour repainted every
 * counter on the server, including ones that had been running for an hour and
 * whose look somebody had deliberately chosen. That is the opposite of what a
 * default is.</p>
 *
 * <p>So a timer takes a copy of the defaults when it is created and keeps it.
 * Changing a default from then on reaches new timers only; changing a timer
 * reaches that timer only. Neither can surprise the other.</p>
 *
 * <p>Mutable on purpose: the panel edits these one field at a time.</p>
 */
public final class TimerDisplay {

    private boolean hideOnCooldown;
    private String preset;
    private int x;
    private int y;
    private float scale;

    private int colorHigh;
    private int colorMid;
    private int colorLow;
    private int thresholdMid;
    private int thresholdLow;

    private String soundId;
    private float soundVolume;
    private float soundPitch;

    private TimerDisplay() {}

    /** A copy of what the config currently offers new timers. */
    public static TimerDisplay fromDefaults() {
        ModConfig config = ModConfig.getInstance();
        TimerDisplay display = new TimerDisplay();
        display.preset = config.getPositionPreset().name();
        display.x = config.getTimerX();
        display.y = config.getTimerY();
        display.scale = config.getTimerScale();
        display.colorHigh = config.getColorHigh();
        display.colorMid = config.getColorMid();
        display.colorLow = config.getColorLow();
        display.thresholdMid = config.getThresholdMid();
        display.thresholdLow = config.getThresholdLow();
        display.hideOnCooldown = config.isHideOnCooldown();
        display.soundId = config.getTimerSoundId();
        display.soundVolume = config.getTimerSoundVolume();
        display.soundPitch = config.getTimerSoundPitch();
        return display;
    }

    /** An independent copy, for cloning a timer. */
    public TimerDisplay copy() {
        TimerDisplay other = new TimerDisplay();
        other.preset = preset;
        other.x = x;
        other.y = y;
        other.scale = scale;
        other.colorHigh = colorHigh;
        other.colorMid = colorMid;
        other.colorLow = colorLow;
        other.thresholdMid = thresholdMid;
        other.thresholdLow = thresholdLow;
        other.hideOnCooldown = hideOnCooldown;
        other.soundId = soundId;
        other.soundVolume = soundVolume;
        other.soundPitch = soundPitch;
        return other;
    }

    /** The colour this timer wears at a given percentage of its span. */
    public int colorFor(float percentage) {
        if (percentage >= thresholdMid) return colorHigh;
        if (percentage >= thresholdLow) return colorMid;
        return colorLow;
    }

    public String preset() { return preset; }
    public int x() { return x; }
    public int y() { return y; }
    public float scale() { return scale; }
    public int colorHigh() { return colorHigh; }
    public int colorMid() { return colorMid; }
    public int colorLow() { return colorLow; }
    public int thresholdMid() { return thresholdMid; }
    public int thresholdLow() { return thresholdLow; }
    public boolean hideOnCooldown() { return hideOnCooldown; }
    public String soundId() { return soundId; }
    public float soundVolume() { return soundVolume; }
    public float soundPitch() { return soundPitch; }

    // Setters clamp to the same bounds the config does, so a value that arrives
    // through the panel cannot land somewhere a value from the config never
    // could.

    public void setPreset(String value) {
        if (value != null && !value.isEmpty()) preset = value;
    }

    public void setX(int value) { x = value; }

    public void setY(int value) { y = Math.max(0, value); }

    public void setScale(float value) { scale = Math.max(0.1f, Math.min(5.0f, value)); }

    public void setColorHigh(int value) { colorHigh = value & 0xFFFFFF; }

    public void setColorMid(int value) { colorMid = value & 0xFFFFFF; }

    public void setColorLow(int value) { colorLow = value & 0xFFFFFF; }

    public void setThresholdMid(int value) { thresholdMid = Math.max(0, Math.min(100, value)); }

    public void setThresholdLow(int value) { thresholdLow = Math.max(0, Math.min(100, value)); }

    public void setHideOnCooldown(boolean value) { hideOnCooldown = value; }

    public void setSoundId(String value) {
        if (value != null && !value.isEmpty()) soundId = value;
    }

    public void setSoundVolume(float value) { soundVolume = Math.max(0f, Math.min(1f, value)); }

    public void setSoundPitch(float value) { soundPitch = Math.max(0.5f, Math.min(2f, value)); }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("preset", preset);
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("scale", scale);
        json.addProperty("colorHigh", colorHigh);
        json.addProperty("colorMid", colorMid);
        json.addProperty("colorLow", colorLow);
        json.addProperty("thresholdMid", thresholdMid);
        json.addProperty("thresholdLow", thresholdLow);
        json.addProperty("hideOnCooldown", hideOnCooldown);
        json.addProperty("soundId", soundId);
        json.addProperty("soundVolume", soundVolume);
        json.addProperty("soundPitch", soundPitch);
        return json;
    }

    /**
     * Reads whatever the file has, leaving anything absent alone.
     *
     * <p>Left alone means "keep what the constructor put there", which is
     * today's default. So a timer written before this existed adopts the
     * current defaults once, at load, and is frozen from then on — the only
     * honest answer, since what it looked like when it was created is not
     * recorded anywhere.</p>
     */
    public void readFrom(JsonObject json) {
        if (json == null) return;
        if (has(json, "preset")) setPreset(json.get("preset").getAsString());
        if (has(json, "x")) x = json.get("x").getAsInt();
        if (has(json, "y")) setY(json.get("y").getAsInt());
        if (has(json, "scale")) setScale(json.get("scale").getAsFloat());
        if (has(json, "colorHigh")) setColorHigh(json.get("colorHigh").getAsInt());
        if (has(json, "colorMid")) setColorMid(json.get("colorMid").getAsInt());
        if (has(json, "colorLow")) setColorLow(json.get("colorLow").getAsInt());
        if (has(json, "thresholdMid")) setThresholdMid(json.get("thresholdMid").getAsInt());
        if (has(json, "thresholdLow")) setThresholdLow(json.get("thresholdLow").getAsInt());
        if (has(json, "hideOnCooldown")) hideOnCooldown = json.get("hideOnCooldown").getAsBoolean();
        if (has(json, "soundId")) setSoundId(json.get("soundId").getAsString());
        if (has(json, "soundVolume")) setSoundVolume(json.get("soundVolume").getAsFloat());
        if (has(json, "soundPitch")) setSoundPitch(json.get("soundPitch").getAsFloat());
    }

    private static boolean has(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull();
    }
}
