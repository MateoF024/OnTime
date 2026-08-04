package com.mateof24.integration;

import com.mateof24.OnTimeConstants;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reflection-based displacement of Jade's overlay (no compileOnly dep needed).
 *
 * <p>None of this is Jade's plugin API — its wiki documents providers,
 * datapacks and themes, and says nothing about the overlay's position config or
 * the renderer. So the surface below was read off the jars themselves, one per
 * loader and version we ship for, and the placement formula off Jade's source
 * ({@code BoxElementImpl.updateExpectedRect}).</p>
 *
 * <p>What actually drifts, measured across all sixteen jars:</p>
 * <ul>
 *   <li>{@code IWailaConfig.getOverlay()} became {@code overlay()} in Jade 18.x
 *       (MC 1.21.5), and {@code IConfigOverlay} was renamed {@code Overlay} with
 *       it. The five accessors we use — overlayPosX/Y, setOverlayPosY,
 *       anchorX/Y — are present and identically named in every one of them.</li>
 *   <li>The rendered rect moved from {@code OverlayRenderer.rect}
 *       (TooltipRect, Rect2i, up to 18.x / MC 1.21.5) to
 *       {@code OverlayRenderer.animation} (TooltipAnimation, Rect2f, from 19.x /
 *       MC 1.21.8). Both expose {@code expectedRect} and {@code rect}.</li>
 *   <li>Jade 11.x (MC 1.20.x, the maintenance branch) has no rect field at all,
 *       so the overlay's size falls back to an estimate there.</li>
 *   <li>{@code accessibility().tryFlip(float)} exists from 18.x onwards; older
 *       Jade has no mirroring option, and leaving the value alone is correct.</li>
 * </ul>
 *
 * Robustness:
 *  - All reflection guarded by Throwable so a missing/incompatible Jade class
 *    can never crash the host (fixes the legacyforge 1.20.1 startup crash).
 *  - userPosY is captured/refreshed only while NOT displacing. While displacing,
 *    we enforce our target unconditionally — this avoids the flicker on loaders
 *    where Jade re-asserts its config every tick (NeoForge 1.21.1 bug).
 *  - On restore we only overwrite if the current Jade value is still the one
 *    WE wrote, so any mid-displacement edits the player makes survive.
 */
public final class JadeOverlayManager {

    private static final float EPSILON = 0.0015f;

    private static volatile Boolean installed = null;
    private static volatile boolean initialized = false;

    private static Object overlayConfigInstance;
    private static Method getPosY, setPosY, getPosX, getAnchorX, getAnchorY;

    // Accessibility mirror, present from Jade 18.x (MC 1.21.5) onwards.
    private static Object accessibilityConfig;
    private static Method tryFlipMethod;

    // Optional access to Jade's last-rendered rect for precise overlap detection.
    // Two layouts are supported:
    //   layout A: OverlayRenderer.rect (TooltipRect) -> .rect (Rect2i: int)
    //   layout B: OverlayRenderer.animation (TooltipAnimation) -> .rect (Rect2f: float)
    private static Field overlayShownField;
    private static Field outerRectField;     // either `rect` or `animation`
    private static Field innerRectField;     // both layouts call the inner field `rect`
    private static Method rectGetX, rectGetY, rectGetWidth, rectGetHeight;

    // Tracked state.
    private static float userPosY = Float.NaN;     // what the player configured
    private static float lastWrittenY = Float.NaN; // the last value WE wrote
    private static boolean displacing = false;

    private JadeOverlayManager() {}

    public static boolean isInstalled() {
        if (installed == null) {
            try {
                Class.forName("snownee.jade.api.config.IWailaConfig");
                installed = true;
            } catch (Throwable t) {
                installed = false;
            }
        }
        return installed;
    }

    public static synchronized boolean tryInit() {
        if (initialized) return true;
        if (!isInstalled()) return false;
        try {
            Class<?> wailaCfgClass = Class.forName("snownee.jade.api.config.IWailaConfig");
            Object cfg = invokeStaticGet(wailaCfgClass);
            if (cfg == null) return false;

            // Jade 18.x (MC 1.21.5) renamed getOverlay() -> overlay(). Try both.
            overlayConfigInstance = invokeAccessor(wailaCfgClass, cfg, "getOverlay", "overlay");
            if (overlayConfigInstance == null) return false;

            Class<?> overlayCfgClass = overlayConfigInstance.getClass();
            getPosY    = lookupAny(overlayCfgClass, "getOverlayPosY", "overlayPosY");
            setPosY    = lookupSetter(overlayCfgClass, "setOverlayPosY");
            getPosX    = lookupAny(overlayCfgClass, "getOverlayPosX", "overlayPosX");
            getAnchorX = lookupAny(overlayCfgClass, "getAnchorX", "anchorX");
            getAnchorY = lookupAny(overlayCfgClass, "getAnchorY", "anchorY");

            if (getPosY == null || setPosY == null) return false;

            // Optional: Jade 11.x and 15.x have no accessibility config at all.
            accessibilityConfig = invokeAccessor(wailaCfgClass, cfg, "getAccessibility", "accessibility");
            if (accessibilityConfig != null) {
                try {
                    tryFlipMethod = accessibilityConfig.getClass().getMethod("tryFlip", float.class);
                    tryFlipMethod.setAccessible(true);
                } catch (Throwable ignored) {
                    tryFlipMethod = null;
                }
            }

            tryInitOverlayRectAccess();

            userPosY = readPosY();
            lastWrittenY = Float.NaN;
            displacing = false;
            initialized = true;
            OnTimeConstants.LOGGER.info("[OnTime/Jade] Initialized (userPosY={}, rectAccess={})",
                    userPosY, outerRectField != null);
            return true;
        } catch (Throwable t) {
            initialized = false;
            OnTimeConstants.LOGGER.debug("[OnTime/Jade] Init deferred: {}", t.toString());
            return false;
        }
    }

    private static Object invokeStaticGet(Class<?> wailaCfgClass) {
        try {
            return wailaCfgClass.getMethod("get").invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object invokeAccessor(Class<?> cls, Object instance, String... candidateNames) {
        for (String name : candidateNames) {
            try {
                Method m = cls.getMethod(name);
                m.setAccessible(true);
                return m.invoke(instance);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Method lookupAny(Class<?> cls, String... candidateNames) {
        for (String name : candidateNames) {
            try {
                Method m = cls.getMethod(name);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {}
        }
        // Fallback: scan all public methods, no-arg, name match.
        try {
            for (Method m : cls.getMethods()) {
                if (m.getParameterCount() == 0) {
                    for (String name : candidateNames) {
                        if (m.getName().equals(name)) {
                            m.setAccessible(true);
                            return m;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Method lookupSetter(Class<?> cls, String name) {
        try {
            Method m = cls.getMethod(name, float.class);
            m.setAccessible(true);
            return m;
        } catch (Throwable ignored) {}
        try {
            for (Method m : cls.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == float.class) {
                    m.setAccessible(true);
                    return m;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void tryInitOverlayRectAccess() {
        Class<?> renderer;
        try {
            renderer = Class.forName("snownee.jade.overlay.OverlayRenderer");
        } catch (Throwable t) {
            return;
        }
        try {
            overlayShownField = renderer.getField("shown");
        } catch (Throwable ignored) {}

        // Try the two known layouts.
        for (String outerName : new String[]{"rect", "animation"}) {
            try {
                Field outer = renderer.getField(outerName);
                Class<?> outerType = outer.getType();
                // Prefer `expectedRect` (settled/target position) over `rect`
                // (mid-animation position). Reading the animated value creates
                // a feedback loop where our displacement chases its own animation.
                Field inner;
                try {
                    inner = outerType.getField("expectedRect");
                } catch (Throwable t) {
                    try {
                        inner = outerType.getField("rect");
                    } catch (Throwable t2) {
                        continue;
                    }
                }
                Class<?> rectCls = inner.getType();
                Method gx = lookupGetterOnRect(rectCls, "getX");
                Method gy = lookupGetterOnRect(rectCls, "getY");
                Method gw = lookupGetterOnRect(rectCls, "getWidth");
                Method gh = lookupGetterOnRect(rectCls, "getHeight");
                if (gx == null || gy == null || gw == null || gh == null) continue;

                outerRectField = outer;
                innerRectField = inner;
                rectGetX = gx;
                rectGetY = gy;
                rectGetWidth = gw;
                rectGetHeight = gh;
                return;
            } catch (Throwable ignored) {}
        }
    }

    private static Method lookupGetterOnRect(Class<?> cls, String name) {
        try {
            Method m = cls.getMethod(name);
            m.setAccessible(true);
            return m;
        } catch (Throwable t) {
            return null;
        }
    }

    private static float readPosY() {
        try {
            return ((Number) getPosY.invoke(overlayConfigInstance)).floatValue();
        } catch (Throwable t) {
            return Float.NaN;
        }
    }

    /** Pixels left between Jade and a counter it had to step around. */
    private static final int MARGIN = 4;

    /**
     * Called every client tick while at least one counter is up.
     *
     * @param timerRects one {left, top, right, bottom} per visible execution,
     *                   never a union of them — see
     *                   {@code TitleBlock.occupiedRects}
     */
    public static void updateForTimers(java.util.List<int[]> timerRects, int screenW, int screenH) {
        if (!isInstalled() || screenH <= 0 || screenW <= 0) return;
        if (timerRects == null || timerRects.isEmpty()) return;
        if (!initialized && !tryInit()) return;

        float realPosY = readPosY();
        if (Float.isNaN(realPosY)) return;

        // Only refresh user's preferred posY when we're NOT actively displacing.
        // While displacing, ignore Jade's value entirely — some loaders (NeoForge 1.21.1)
        // have Jade re-assert its config every tick, which previously caused flicker.
        if (!displacing) {
            userPosY = realPosY;
        }

        if (Float.isNaN(userPosY)) userPosY = realPosY;

        Box jade = measure(screenW, screenH);

        // Only counters that share Jade's horizontal band can ever be in the
        // way. Moving Jade vertically never changes its X, so this set is fixed
        // for the whole search — and a counter that lines up in height but not
        // in width is not a collision at all.
        java.util.List<int[]> inColumn = new java.util.ArrayList<>();
        for (int[] rect : timerRects) {
            if (rect[2] > jade.left && rect[0] < jade.right) inColumn.add(rect);
        }

        Float target = solve(inColumn, jade, screenH);
        if (target == null) {
            if (displacing) restoreInternal();
            return;
        }

        if (Math.abs(realPosY - target) <= EPSILON) {
            displacing = true;
            lastWrittenY = target;
            return;
        }
        try {
            setPosY.invoke(overlayConfigInstance, target);
            lastWrittenY = target;
            displacing = true;
        } catch (Throwable t) {
            OnTimeConstants.LOGGER.debug("[OnTime/Jade] setOverlayPosY failed: {}", t.toString());
        }
    }

    /**
     * The posY that clears every counter, or null when the user's own value
     * already does.
     *
     * <p>Stepping down past one counter can land on the next, so the search
     * repeats; each step strictly lowers Jade, so it cannot loop. Down is tried
     * first — it is what the boss-bar case has always done and what Jade's own
     * PUSH_DOWN does — and up only when down would push Jade off the bottom of
     * the screen, which is what a counter pinned near the bottom would do.</p>
     */
    static Float solve(java.util.List<int[]> inColumn, Box jade, int screenH) {
        if (inColumn.isEmpty()) return null;
        if (collisionAt(inColumn, jade, jade.top) == null) return null;

        float top = jade.top;
        for (int step = 0; step <= inColumn.size(); step++) {
            int[] hit = collisionAt(inColumn, jade, top);
            if (hit == null) return posYForTop(top, jade, screenH);
            float next = hit[3] + MARGIN;
            if (next + jade.height > screenH) break;
            top = next;
        }

        top = jade.top;
        for (int step = 0; step <= inColumn.size(); step++) {
            int[] hit = collisionAt(inColumn, jade, top);
            if (hit == null) return posYForTop(top, jade, screenH);
            float next = hit[1] - MARGIN - jade.height;
            if (next < 0f) return null; // boxed in: leave the player's value alone
            top = next;
        }
        return null;
    }

    /** First counter Jade's box would overlap with its top edge at {@code top}. */
    static int[] collisionAt(java.util.List<int[]> inColumn, Box jade, float top) {
        float bottom = top + jade.height;
        for (int[] rect : inColumn) {
            if (rect[3] > top && rect[1] < bottom) return rect;
        }
        return null;
    }

    /**
     * Inverts Jade's own placement: it renders at
     * {@code screenH * (1 - posY) - height * anchorY}, so this is that solved
     * for posY. Deliberately derived from the counter positions and Jade's
     * dimensions only — never from Jade's current Y, which is our own last
     * displacement and would make each tick chase the previous one.
     */
    private static Float posYForTop(float top, Box jade, int screenH) {
        float posY = 1f - (top + jade.height * jade.anchorY) / screenH;
        return Math.max(0f, Math.min(1f, posY));
    }

    /** Called when the timer is gone or hidden. Restores the user's preferred posY. */
    public static void restore() {
        if (!initialized || !displacing) return;
        restoreInternal();
    }

    private static void restoreInternal() {
        if (Float.isNaN(userPosY)) {
            displacing = false;
            return;
        }
        try {
            float real = readPosY();
            // Only restore if the value out there is what we last wrote. If the
            // player edited mid-displacement, their value already won — leave it.
            if (Float.isNaN(lastWrittenY) || Math.abs(real - lastWrittenY) <= EPSILON) {
                if (Math.abs(real - userPosY) > EPSILON) {
                    setPosY.invoke(overlayConfigInstance, userPosY);
                }
            } else {
                userPosY = real;
            }
        } catch (Throwable t) {
            OnTimeConstants.LOGGER.debug("[OnTime/Jade] restore failed: {}", t.toString());
        }
        displacing = false;
        lastWrittenY = Float.NaN;
    }

    /** Jade's box as the player configured it, before any displacement of ours. */
    static final class Box {
        float left, right, top, height, anchorY;
    }

    /**
     * Where Jade would draw itself at the player's own posY.
     *
     * <p>Jade cannot be asked directly — its live rect already reflects
     * whatever we last wrote — so this reproduces its placement:
     * {@code x = screenW * flip(posX)}, {@code y = screenH * (1 - posY)}, then
     * {@code left = x - w * flip(anchorX)} and {@code top = y - h * anchorY}.
     * Width and height come from the live rect because they do not depend on
     * the position at all, and they already include Jade's own scale.</p>
     */
    private static Box measure(int screenW, int screenH) {
        float[] live = readActualRect();
        Box box = new Box();
        float width = (live != null && live[2] > 0) ? live[2] : 100f;
        box.height = (live != null && live[3] > 0) ? live[3] : 22f;

        box.anchorY = readFloatOrDefault(getAnchorY, 0f);
        float anchorX = tryFlip(readFloatOrDefault(getAnchorX, 0.5f));
        float posX = tryFlip(readFloatOrDefault(getPosX, 0.5f));

        float anchoredX = screenW * posX;
        box.left = anchoredX - width * anchorX;
        box.right = box.left + width;
        box.top = screenH * (1f - userPosY) - box.height * box.anchorY;
        return box;
    }

    /**
     * Jade's accessibility mirror, applied to the X axis when the player has
     * "flip main hand" on and plays left-handed. Asking Jade to do it rather
     * than reimplementing it means the two cannot disagree; older Jade has no
     * such option and leaves the value alone.
     */
    private static float tryFlip(float value) {
        if (accessibilityConfig == null || tryFlipMethod == null) return value;
        try {
            return ((Number) tryFlipMethod.invoke(accessibilityConfig, value)).floatValue();
        } catch (Throwable t) {
            return value;
        }
    }

    private static float readFloatOrDefault(Method m, float fallback) {
        if (m == null) return fallback;
        try {
            return ((Number) m.invoke(overlayConfigInstance)).floatValue();
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** @return [x,y,w,h] of Jade's last rendered rect (as floats), or null. */
    private static float[] readActualRect() {
        if (outerRectField == null || innerRectField == null) return null;
        try {
            if (overlayShownField != null) {
                Object shown = overlayShownField.get(null);
                if (shown instanceof Boolean && !(Boolean) shown) return null;
            }
            Object outer = outerRectField.get(null);
            if (outer == null) return null;
            Object rect = innerRectField.get(outer);
            if (rect == null) return null;
            float x = ((Number) rectGetX.invoke(rect)).floatValue();
            float y = ((Number) rectGetY.invoke(rect)).floatValue();
            float w = ((Number) rectGetWidth.invoke(rect)).floatValue();
            float h = ((Number) rectGetHeight.invoke(rect)).floatValue();
            return new float[]{x, y, w, h};
        } catch (Throwable t) {
            return null;
        }
    }

    /** Hard reset — used on world disconnect. Restores user value, clears all state. */
    public static void resetOnDisconnect() {
        if (!initialized) return;
        if (displacing) restoreInternal();
        try {
            float real = readPosY();
            if (!Float.isNaN(real)) userPosY = real;
        } catch (Throwable ignored) {}
        lastWrittenY = Float.NaN;
        displacing = false;
    }
}
