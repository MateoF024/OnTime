package com.mateof24.manager;

import com.mateof24.OnTimeConstants;
import com.mateof24.config.ModConfig;
import com.mateof24.storage.TimerStorage;
import com.mateof24.timer.Timer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TimerManager {
    private static final TimerManager INSTANCE = new TimerManager();
    private final Map<String, Timer> timers = new HashMap<>();
    private Timer activeTimer = null;

    private TimerManager() {}

    public static TimerManager getInstance() {
        return INSTANCE;
    }

    public boolean createTimer(String name, int hours, int minutes, int seconds, boolean countUp) {
        if (timers.containsKey(name)) {
            return false;
        }

        long totalSeconds = hours * 3600L + minutes * 60L + seconds;
        long maxSeconds = ModConfig.getInstance().getMaxTimerSeconds();

        if (totalSeconds > maxSeconds) {
            return false;
        }

        Timer timer = new Timer(name, hours, minutes, seconds, countUp);
        timers.put(name, timer);
        saveTimers();
        return true;
    }

    public boolean removeTimer(String name) {
        if (!timers.containsKey(name)) {
            return false;
        }

        Timer timer = timers.get(name);
        if (timer == activeTimer) {
            activeTimer = null;
        }

        timers.remove(name);
        saveTimers();
        return true;
    }

    public boolean startTimer(String name) {
        // Recargar TODO desde disco (no solo comandos)
        reloadFromDisk();

        Timer timer = timers.get(name);
        if (timer == null) {
            return false;
        }

        if (activeTimer != null) {
            activeTimer.setRunning(false);
        }

        timer.setRunning(true);
        activeTimer = timer;
        saveTimers();
        return true;
    }

    public boolean pauseTimer() {
        if (activeTimer == null) {
            return false;
        }

        activeTimer.setRunning(false);
        saveTimers();
        return true;
    }

    public boolean setTimerTime(String name, int hours, int minutes, int seconds) {
        Timer timer = timers.get(name);
        if (timer == null) {
            return false;
        }

        timer.setTime(hours, minutes, seconds);
        saveTimers();
        return true;
    }

    public boolean addTimerTime(String name, int hours, int minutes, int seconds) {
        Timer timer = timers.get(name);
        if (timer == null) {
            return false;
        }

        timer.addTime(hours, minutes, seconds);
        saveTimers();
        return true;
    }

    public Optional<Timer> getTimer(String name) {
        return Optional.ofNullable(timers.get(name));
    }

    public Optional<Timer> getActiveTimer() {
        return Optional.ofNullable(activeTimer);
    }

    public boolean hasTimer(String name) {
        return timers.containsKey(name);
    }

    public Map<String, Timer> getAllTimers() {
        return new HashMap<>(timers);
    }

    public void clearActiveTimer() {
        activeTimer = null;
    }

    public void saveTimers() {
        String activeTimerName = activeTimer != null ? activeTimer.getName() : null;
        TimerStorage.saveTimers(timers, activeTimerName);
    }

    public void loadTimers() {
        timers.clear();
        activeTimer = null;

        TimerStorage.TimerLoadResult result = TimerStorage.loadTimers();
        timers.putAll(result.getTimers());

        String activeTimerName = result.getActiveTimerName();
        if (activeTimerName != null && timers.containsKey(activeTimerName)) {
            activeTimer = timers.get(activeTimerName);
            OnTimeConstants.LOGGER.info("Restored active timer: '{}'", activeTimerName);
        }
        validateActiveTimer();
    }

    /**
     * Recarga los datos de los timers desde disco (comandos, sonidos, etc.)
     * Preserva el estado de running y currentTicks
     */
    public void reloadCommandsFromDisk() {
        TimerStorage.TimerLoadResult result = TimerStorage.loadTimers();
        Map<String, Timer> diskTimers = result.getTimers();

        for (Map.Entry<String, Timer> entry : diskTimers.entrySet()) {
            String name = entry.getKey();
            Timer diskTimer = entry.getValue();
            Timer memTimer = timers.get(name);

            if (memTimer != null) {
                // Actualizar comando
                memTimer.setCommand(diskTimer.getCommand());

                // Actualizar configuración de sonido
                memTimer.setSoundId(diskTimer.getSoundId());
                memTimer.setSoundVolume(diskTimer.getSoundVolume());
                memTimer.setSoundPitch(diskTimer.getSoundPitch());

                // Actualizar otras configuraciones
                memTimer.setSilent(diskTimer.isSilent());
            }
        }
    }

    /**
     * Recarga TODOS los datos de los timers desde disco, preservando el estado de running
     */
    public void reloadFromDisk() {
        TimerStorage.TimerLoadResult result = TimerStorage.loadTimers();
        Map<String, Timer> diskTimers = result.getTimers();

        for (Map.Entry<String, Timer> entry : diskTimers.entrySet()) {
            String name = entry.getKey();
            Timer diskTimer = entry.getValue();
            Timer memTimer = timers.get(name);

            if (memTimer != null) {
                // Preservar el estado de running actual
                boolean wasRunning = memTimer.isRunning();

                // Reemplazar el timer en memoria con el del disco
                timers.put(name, diskTimer);

                // Restaurar el estado de running
                diskTimer.setRunning(wasRunning);

                // Actualizar activeTimer si era este
                if (activeTimer == memTimer) {
                    activeTimer = diskTimer;
                }
            } else {
                // Timer nuevo que no existía en memoria
                timers.put(name, diskTimer);
            }
        }
    }

    public boolean validateActiveTimer() {
        if (activeTimer != null && !timers.containsValue(activeTimer)) {
            OnTimeConstants.LOGGER.warn("Active timer '{}' not found in timers map, clearing", activeTimer.getName());
            activeTimer = null;
            saveTimers();
            return false;
        }
        return true;
    }
}