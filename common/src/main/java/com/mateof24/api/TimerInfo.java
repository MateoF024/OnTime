package com.mateof24.api;

public record TimerInfo(
        String name,
        long currentTicks,
        long targetTicks,
        boolean countUp,
        boolean running,
        boolean silent,
        String command
) {
    public long getCurrentSeconds() { return currentTicks / 20L; }
    public long getTargetSeconds() { return targetTicks / 20L; }

    public String getFormattedTime() {
        long total = getCurrentSeconds();
        long h = total / 3600, m = (total % 3600) / 60, s = total % 60;
        return h > 0 ? String.format("%02d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }

    public float getPercentage() {
        if (targetTicks == 0) return 100f;
        float pct = (currentTicks * 100f) / targetTicks;
        return countUp ? 100f - pct : pct;
    }
}