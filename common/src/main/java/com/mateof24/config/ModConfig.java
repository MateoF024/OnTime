package com.mateof24.config;

import com.google.gson.*;
import com.mateof24.OnTimeConstants;
import com.mateof24.platform.Services;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    // Creación de archivo de configuración principal.
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = Services.PLATFORM.getConfigDir().resolve("ontime");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");

    private static ModConfig INSTANCE;

    private ModConfig() {}

    /**
     * Lazily creates and loads the instance. Callers must NOT call
     * {@link #load()} again right after: the config is already on disk-state
     * here, and the extra read was pure duplicated I/O at startup.
     */
    public static ModConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ModConfig();
            INSTANCE.load();
        }
        return INSTANCE;
    }

    // Variables Configurables (Valores Predeterminados)
    private int timerX = -1;
    private int timerY = 4;
    private TimerPositionPreset positionPreset = TimerPositionPreset.BOSSBAR;
    private float timerScale = 1.0f;
    private long maxTimerSeconds = 86400;
    private int colorHigh = 0xFFFFFF;
    private int colorMid = 0xFFFF00;
    private int colorLow = 0xFF0000;
    private int thresholdMid = 30;
    private int thresholdLow = 10;
    private String timerSoundId = "minecraft:block.note_block.hat";
    private float timerSoundVolume = 1.0f;
    private float timerSoundPitch = 2.0f;
    /**
     * Whether a counter waiting out a cooldown disappears instead of sitting
     * there stopped.
     *
     * <p>On by default: a timer frozen between repeats reads as a timer that
     * has broken, and the only way to tell the difference was to wait and see
     * whether it started again.</p>
     */
    private boolean hideOnCooldown = true;
    private boolean webSocketEnabled = false;
    // Defaults adjacent to Minecraft's port range (25565 main, 25575 RCON) so
    // they group naturally in firewall/port-forwarding rules and avoid the
    // heavy collision footprint of 8080 (Pl3xMap, Tomcat, Spring Boot, etc.).
    private int webSocketPort = 25581;
    private int webPanelPort = 25580;
    // Interface the web panel binds to. "0.0.0.0" keeps it reachable from the
    // LAN (the common case: opening the panel from a phone); "127.0.0.1"
    // restricts it to the machine running the server. File-only on purpose —
    // it is not editable from the panel itself.
    private String webPanelBindAddress = "0.0.0.0";
    /**
     * Where the event feed listens.
     *
     * <p>Same default and same reasoning as the web panel: every
     * interface, because an operator usually wants to reach it from
     * elsewhere, and 127.0.0.1 for anyone who does not. Before 5.0.0 this
     * had no setting and no token either.</p>
     */
    private String webSocketBindAddress = "0.0.0.0";
    // Global pause in ticks between commands that run as a sequence at the
    // SAME moment (a scheduled point or the finish list). 0 = all in one
    // tick, the pre-4.0.0 behavior.
    private int commandDelayTicks = 0;
    // How many executions a single command may create before it has to be
    // confirmed. The gate is on the count, not on the selector: /timer start x
    // @a is one execution and never asks, /timer start x @a each is one per
    // player and does. 0 = always confirm, -1 = never.
    private int confirmRunThreshold = 8;


    // Función para cargar parámetros
    public void load() {
        if (!Files.exists(CONFIG_FILE)) {
            // First run: write the defaults straight away rather than waiting
            // for a flush, so the file is there for the user to edit.
            save();
            return;
        }
        try (java.io.Reader reader = com.mateof24.storage.AtomicJsonIO.newReader(CONFIG_FILE)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;
            if (root.has("timerX")) {
                timerX = root.get("timerX").getAsInt();
            }
            if (root.has("timerY")) {
                timerY = root.get("timerY").getAsInt();
                timerY = Math.max(0, timerY);
            }
            if (root.has("timerScale")) {
                timerScale = root.get("timerScale").getAsFloat();
                timerScale = Math.max(0.1f, Math.min(5.0f, timerScale));
            }
            if (root.has("positionPreset")) {
                String presetName = root.get("positionPreset").getAsString();
                positionPreset = TimerPositionPreset.fromString(presetName);
            }
            if (root.has("maxTimerSeconds")) maxTimerSeconds = root.get("maxTimerSeconds").getAsLong();
            if (root.has("colorHigh")) colorHigh = parseColor(root.get("colorHigh").getAsString());
            if (root.has("colorMid")) colorMid = parseColor(root.get("colorMid").getAsString());
            if (root.has("colorLow")) colorLow = parseColor(root.get("colorLow").getAsString());
            if (root.has("thresholdMid")) thresholdMid = root.get("thresholdMid").getAsInt();
            if (root.has("thresholdLow")) thresholdLow = root.get("thresholdLow").getAsInt();
            if (root.has("timerSoundId")) timerSoundId = root.get("timerSoundId").getAsString();
            if (root.has("timerSoundVolume")) timerSoundVolume = root.get("timerSoundVolume").getAsFloat();
            if (root.has("timerSoundPitch")) timerSoundPitch = root.get("timerSoundPitch").getAsFloat();
            if (root.has("webSocketEnabled")) webSocketEnabled = root.get("webSocketEnabled").getAsBoolean();
            if (root.has("webSocketPort")) webSocketPort = root.get("webSocketPort").getAsInt();
            if (root.has("webPanelPort")) webPanelPort = root.get("webPanelPort").getAsInt();
            if (root.has("webPanelBindAddress")) {
                String bind = root.get("webPanelBindAddress").getAsString().trim();
                if (!bind.isEmpty()) webPanelBindAddress = bind;
            }
            if (root.has("webSocketBindAddress")) {
                String bind = root.get("webSocketBindAddress").getAsString().trim();
                if (!bind.isEmpty()) webSocketBindAddress = bind;
            }
            if (root.has("hideOnCooldown")) {
                hideOnCooldown = root.get("hideOnCooldown").getAsBoolean();
            }
            if (root.has("commandDelayTicks")) {
                commandDelayTicks = root.get("commandDelayTicks").getAsInt();
                commandDelayTicks = Math.max(0, Math.min(1200, commandDelayTicks));
            }
            if (root.has("confirmRunThreshold")) {
                confirmRunThreshold = Math.max(-1, root.get("confirmRunThreshold").getAsInt());
            }
        } catch (IOException e) {
            OnTimeConstants.LOGGER.error("Failed to load config", e);
        }

    }

    /**
     * Set by the setters, cleared by {@link #save()}. Every setter used to
     * write the file on the spot, so saving a config screen produced one disk
     * write per field touched.
     */
    private boolean dirty = false;

    /**
     * Every setting back to what the mod ships with.
     *
     * <p>Copied from a fresh instance rather than from a second list of
     * literals: the field initialisers above are the only place a default is
     * written down, so this cannot drift away from them the way a hand-written
     * copy would. {@code dirty} and the instance itself are skipped — the
     * first is bookkeeping and the second is not a setting.</p>
     *
     * <p>Timers already created keep what they have. They took their copy when
     * they were made, which is the whole point of the defaults being defaults.
     * </p>
     */
    public void restoreDefaults() {
        ModConfig fresh = new ModConfig();
        for (java.lang.reflect.Field field : ModConfig.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (java.lang.reflect.Modifier.isStatic(modifiers)) continue;
            if (java.lang.reflect.Modifier.isFinal(modifiers)) continue;
            if ("dirty".equals(field.getName())) continue;
            try {
                field.setAccessible(true);
                field.set(this, field.get(fresh));
            } catch (ReflectiveOperationException | RuntimeException e) {
                com.mateof24.OnTimeConstants.LOGGER.warn(
                        "Could not restore the default of '{}'", field.getName(), e);
            }
        }
        dirty = true;
    }

    public boolean isHideOnCooldown() { return hideOnCooldown; }

    public void setHideOnCooldown(boolean value) {
        if (hideOnCooldown == value) return;
        hideOnCooldown = value;
        dirty = true;
    }

    /** Writes only if a setter actually changed something. */
    public void flush() {
        if (!dirty) return;
        save();
    }


    // Función para guardar parámetros
    public void save() {
        dirty = false;
        try {
            Files.createDirectories(CONFIG_DIR);
            JsonObject root = new JsonObject();
            root.addProperty("timerX", timerX);
            root.addProperty("timerY", timerY);
            root.addProperty("timerScale", timerScale);
        root.addProperty("hideOnCooldown", hideOnCooldown);
            root.addProperty("positionPreset", positionPreset.name());
            root.addProperty("maxTimerSeconds", maxTimerSeconds);
            root.addProperty("colorHigh", String.format("#%06X", colorHigh));
            root.addProperty("colorMid", String.format("#%06X", colorMid));
            root.addProperty("colorLow", String.format("#%06X", colorLow));
            root.addProperty("thresholdMid", thresholdMid);
            root.addProperty("thresholdLow", thresholdLow);
            root.addProperty("timerSoundId", timerSoundId);
            root.addProperty("timerSoundVolume", timerSoundVolume);
            root.addProperty("timerSoundPitch", timerSoundPitch);
            root.addProperty("webSocketEnabled", webSocketEnabled);
            root.addProperty("webSocketPort", webSocketPort);
            root.addProperty("webPanelPort", webPanelPort);
            root.addProperty("webPanelBindAddress", webPanelBindAddress);
        root.addProperty("webSocketBindAddress", webSocketBindAddress);
            root.addProperty("commandDelayTicks", commandDelayTicks);
            root.addProperty("confirmRunThreshold", confirmRunThreshold);
            com.mateof24.storage.AtomicJsonIO.write(GSON, CONFIG_FILE, root);
        } catch (IOException e) {
            OnTimeConstants.LOGGER.error("Failed to save config", e);
        }
    }


    // Métodos para obtener y establecer presets de posición.
    public TimerPositionPreset getPositionPreset() {
        return positionPreset;
    }
    public void setPositionPreset(TimerPositionPreset preset) {
        this.positionPreset = preset;

        dirty = true;
    }
    public void applyPreset(TimerPositionPreset preset, int screenWidth, int screenHeight,
                            int timerWidth, int timerHeight) {
        this.positionPreset = preset;


        this.timerX = preset.calculateX(screenWidth, timerWidth, this.timerX);
        this.timerY = preset.calculateY(screenHeight, timerHeight, this.timerY);

        dirty = true;
    }

    // Métodos para obtener y establecer posición

    public int getTimerX() { return timerX; }
    public int getTimerY() { return timerY; }
    public void setTimerX(int x) {
        this.timerX = x;
        dirty = true;
    }
    public void setTimerY(int y) {
        this.timerY = Math.max(0, y);
        dirty = true;
    }

    public void setCustomPosition(int x, int y) {
        this.positionPreset = TimerPositionPreset.CUSTOM;
        this.timerX = x;
        this.timerY = Math.max(0, y);
        dirty = true;
    }
    public void setCustomPositionX(int x) {
        this.positionPreset = TimerPositionPreset.CUSTOM;
        this.timerX = x;
        dirty = true;
    }
    public void setCustomPositionY(int y) {
        this.positionPreset = TimerPositionPreset.CUSTOM;
        this.timerY = Math.max(0, y);
        dirty = true;
    }

    // Métodos para obtener y establecer escalas.

    public float getTimerScale() { return timerScale; }
    public void setTimerScale(float scale) {
        this.timerScale = Math.max(0.1f, Math.min(5.0f, scale));
        dirty = true;
    }


    // Métodos para obtener y establecer tiempos.

    public long getMaxTimerSeconds() { return maxTimerSeconds; }
    public void setMaxTimerSeconds(long seconds) { this.maxTimerSeconds = Math.max(1, seconds); dirty = true; }

    // Métodos para obtener y establecer colores

    private int parseColor(String hex) {
        try { return Integer.parseInt(hex.replace("#", ""), 16); }
        catch (NumberFormatException e) { return 0xFFFFFF; }
    }

    public int getColorForPercentage(float percentage) {
        if (percentage >= thresholdMid) {
            return colorHigh;
        } else if (percentage >= thresholdLow) {
            return colorMid;
        } else {
            return colorLow;
        }
    }

    public int getColorHigh() { return colorHigh; }
    public void setColorHigh(int color) { this.colorHigh = color; dirty = true; }

    public int getColorMid() { return colorMid; }
    public void setColorMid(int color) { this.colorMid = color; dirty = true; }

    public int getColorLow() { return colorLow; }
    public void setColorLow(int color) { this.colorLow = color; dirty = true; }

    public int getThresholdMid() { return thresholdMid; }
    public void setThresholdMid(int threshold) { this.thresholdMid = threshold; dirty = true; }

    public int getThresholdLow() { return thresholdLow; }
    public void setThresholdLow(int threshold) { this.thresholdLow = threshold; dirty = true; }

    // Métodos para obtener y establecer sonido, volumen y pitch

    public String getTimerSoundId() { return timerSoundId; }
    public void setTimerSoundId(String soundId) { this.timerSoundId = soundId; dirty = true; }

    public float getTimerSoundVolume() { return timerSoundVolume; }
    public void setTimerSoundVolume(float volume) { this.timerSoundVolume = volume; dirty = true; }

    public float getTimerSoundPitch() { return timerSoundPitch; }
    public void setTimerSoundPitch(float pitch) { this.timerSoundPitch = pitch; dirty = true; }
    public void setTimerSound(String soundId, float volume, float pitch) {
        this.timerSoundId = soundId;
        this.timerSoundVolume = volume;
        this.timerSoundPitch = pitch;
        dirty = true;
    }

    public boolean isWebSocketEnabled() { return webSocketEnabled; }
    public void setWebSocketEnabled(boolean enabled) { this.webSocketEnabled = enabled; dirty = true; }
    public int getWebSocketPort() { return webSocketPort; }
    public void setWebSocketPort(int port) { this.webSocketPort = port; dirty = true; }
    public int getWebPanelPort() { return webPanelPort; }
    public void setWebPanelPort(int port) { this.webPanelPort = port; dirty = true; }
    public String getWebPanelBindAddress() { return webPanelBindAddress; }

    public String getWebSocketBindAddress() { return webSocketBindAddress; }

    public int getConfirmRunThreshold() { return confirmRunThreshold; }
    public void setConfirmRunThreshold(int threshold) {
        int clamped = Math.max(-1, threshold);
        if (clamped == confirmRunThreshold) return;
        this.confirmRunThreshold = clamped;
        dirty = true;
    }

    public int getCommandDelayTicks() { return commandDelayTicks; }
    public void setCommandDelayTicks(int ticks) {
        this.commandDelayTicks = Math.max(0, Math.min(1200, ticks));
        dirty = true;
    }
}