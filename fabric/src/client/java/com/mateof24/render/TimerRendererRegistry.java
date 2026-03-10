package com.mateof24.render;

public class TimerRendererRegistry {

    private static ITimerRenderer customRenderer = null;

    public static void register(ITimerRenderer renderer) { customRenderer = renderer; }
    public static void unregister() { customRenderer = null; }
    public static boolean hasCustomRenderer() { return customRenderer != null; }
    public static ITimerRenderer getCustomRenderer() { return customRenderer; }
}