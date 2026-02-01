package com.mateof24.config;

public enum TimerPositionPreset {
    CUSTOM(-999, -999),           // Posición personalizada (mantiene x, y actuales)
    BOSSBAR(-1, 4),               // Posición por defecto actual
    ACTIONBAR(-1, -999),          // Será calculado dinámicamente
    TOP_LEFT(4, 4),
    TOP_CENTER(-1, 4),
    TOP_RIGHT(-999, 4),           // Será calculado dinámicamente
    CENTER(-1, -999),             // Será calculado dinámicamente
    BOTTOM_LEFT(4, -999),         // Será calculado dinámicamente
    BOTTOM_CENTER(-1, -999),      // Será calculado dinámicamente
    BOTTOM_RIGHT(-999, -999);     // Será calculado dinámicamente

    private final int x;
    private final int y;

    TimerPositionPreset(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Calcula la posición X real basada en el preset
     * @param screenWidth Ancho de la pantalla
     * @param timerWidth Ancho del timer renderizado
     * @param currentX Posición X actual (para CUSTOM)
     * @return Posición X calculada
     */
    public int calculateX(int screenWidth, int timerWidth, int currentX) {
        switch (this) {
            case CUSTOM:
                return currentX;
            case TOP_RIGHT:
            case BOTTOM_RIGHT:
                return screenWidth - timerWidth - 4;
            case TOP_CENTER:
            case CENTER:
            case BOTTOM_CENTER:
            case BOSSBAR:
            case ACTIONBAR:
                return -1; // Centrado
            default:
                return this.x;
        }
    }

    /**
     * Calcula la posición Y real basada en el preset
     * @param screenHeight Altura de la pantalla
     * @param timerHeight Altura del timer renderizado
     * @param currentY Posición Y actual (para CUSTOM)
     * @return Posición Y calculada
     */
    public int calculateY(int screenHeight, int timerHeight, int currentY) {
        switch (this) {
            case CUSTOM:
                return currentY;
            case ACTIONBAR:
                return screenHeight - 59; // Justo encima de la hotbar
            case CENTER:
                return (screenHeight - timerHeight) / 2;
            case BOTTOM_LEFT:
            case BOTTOM_CENTER:
            case BOTTOM_RIGHT:
                return screenHeight - timerHeight - 4;
            default:
                return this.y;
        }
    }

    /**
     * Obtiene el preset por nombre (case-insensitive)
     */
    public static TimerPositionPreset fromString(String name) {
        try {
            return TimerPositionPreset.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BOSSBAR; // Default
        }
    }

    /**
     * Nombre amigable para el usuario
     */
    public String getDisplayName() {
        switch (this) {
            case CUSTOM: return "Custom";
            case BOSSBAR: return "Boss Bar";
            case ACTIONBAR: return "Action Bar";
            case TOP_LEFT: return "Top Left";
            case TOP_CENTER: return "Top Center";
            case TOP_RIGHT: return "Top Right";
            case CENTER: return "Center";
            case BOTTOM_LEFT: return "Bottom Left";
            case BOTTOM_CENTER: return "Bottom Center";
            case BOTTOM_RIGHT: return "Bottom Right";
            default: return name();
        }
    }
}