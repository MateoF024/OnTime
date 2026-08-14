package com.mateof24.render;

import java.lang.reflect.Method;

public class TimerRendererRegistry {

    private static ITimerRenderer customRenderer = null;

    /**
     * Installs a renderer, or refuses it.
     *
     * <p>Both {@code render} overloads are {@code default} so that a 4.x
     * renderer and a 5.x one are equally valid, which leaves one way to get it
     * wrong: implementing neither. That would throw on every frame from deep in
     * the HUD. Catching it here turns a crash loop into one refusal with a
     * sentence saying what to do.</p>
     */
    public static void register(ITimerRenderer renderer) {
        if (renderer != null && !implementsSomething(renderer)) {
            com.mateof24.OnTimeConstants.LOGGER.error(
                    "Refusing renderer {}: it implements neither render(...) overload of ITimerRenderer",
                    renderer.getClass().getName());
            return;
        }
        customRenderer = renderer;
    }

    public static void unregister() { customRenderer = null; }
    public static boolean hasCustomRenderer() { return customRenderer != null; }
    public static ITimerRenderer getCustomRenderer() { return customRenderer; }

    private static boolean implementsSomething(ITimerRenderer renderer) {
        for (Method method : renderer.getClass().getMethods()) {
            if (!method.getName().equals("render")) continue;
            if (method.getDeclaringClass() != ITimerRenderer.class) return true;
        }
        return false;
    }
}
