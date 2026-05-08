package com.mateof24.integration;

import com.mateof24.OnTimeConstants;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reflection-based displacement of Jade's overlay (no compileOnly dep needed).
 *
 * Supports Jade across the full version range we ship for:
 *  - Jade 11.x  (1.20.1)            : OverlayRenderer has no rect field
 *  - Jade 15.x  (1.21.1 NeoForge/Fabric): IConfigOverlay + OverlayRenderer.rect (TooltipRect.rect Rect2i)
 *  - Jade 19.x  (1.21.6+/1.21.8)    : Overlay (renamed) + OverlayRenderer.animation (TooltipAnimation.rect Rect2f)
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

            // Jade 1.21.6+ renamed getOverlay() -> overlay(). Try both.
            overlayConfigInstance = invokeAccessor(wailaCfgClass, cfg, "getOverlay", "overlay");
            if (overlayConfigInstance == null) return false;

            Class<?> overlayCfgClass = overlayConfigInstance.getClass();
            getPosY    = lookupAny(overlayCfgClass, "getOverlayPosY", "overlayPosY");
            setPosY    = lookupSetter(overlayCfgClass, "setOverlayPosY");
            getPosX    = lookupAny(overlayCfgClass, "getOverlayPosX", "overlayPosX");
            getAnchorX = lookupAny(overlayCfgClass, "getAnchorX", "anchorX");
            getAnchorY = lookupAny(overlayCfgClass, "getAnchorY", "anchorY");

            if (getPosY == null || setPosY == null) return false;

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

    /**
     * Called every client tick while the timer is up.
     */
    public static void updateForTimer(int timerLeft, int timerTop, int timerRight, int timerBottom,
                                      int screenW, int screenH) {
        if (!isInstalled() || screenH <= 0 || screenW <= 0) return;
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

        if (!wouldOverlapAt(timerLeft, timerTop, timerRight, timerBottom, screenW, screenH, userPosY)) {
            if (displacing) restoreInternal();
            return;
        }

        // posY-based displacement target. We deliberately do NOT key off Jade's
        // currently-rendered rect: that creates a feedback loop where each tick
        // we re-measure our own displacement and recompute, causing the overlay
        // to oscillate. Slight over-displacement (when anchorY > 0) is fine.
        float ratio = (float) (timerBottom + 4) / (float) screenH;
        float desired = 1f - Math.max(0f, Math.min(1f, ratio));

        // Never push higher than the user's preferred value (smaller posY = lower).
        float target = Math.min(userPosY, desired);
        if (target < 0f) target = 0f;
        if (target > 1f) target = 1f;

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

    /**
     * Would Jade overlap the timer if it were rendered at the user's preferred posY?
     * We can't ask Jade directly (its current rect reflects whatever posY we wrote),
     * so we estimate from posY/posX/anchor + the actual rendered width (when readable).
     */
    private static boolean wouldOverlapAt(int tLeft, int tTop, int tRight, int tBottom,
                                          int screenW, int screenH, float posY) {
        // Jade's current rect gives us the actual width/height of its overlay,
        // which doesn't depend on posY/posX. Use those dims to estimate the
        // bounding box at the user's preferred position.
        float[] live = readActualRect();
        float jadeW = (live != null && live[2] > 0) ? live[2] : 100f;
        float jadeH = (live != null && live[3] > 0) ? live[3] : 22f;

        float anchorY = readFloatOrDefault(getAnchorY, 0f);
        float anchorX = readFloatOrDefault(getAnchorX, 0.5f);
        float posX    = readFloatOrDefault(getPosX, 0.5f);

        // Estimated top edge at the given posY:
        //   anchored Y in pixels = screenH * (1 - posY)
        //   render top = anchoredY - jadeH * anchorY
        float estTop = screenH * (1f - posY) - jadeH * anchorY;
        float estBottom = estTop + jadeH;
        if (estTop > tBottom || estBottom < tTop) return false;

        float anchoredX = screenW * posX;
        float estLeft = anchoredX - jadeW * anchorX;
        float estRight = anchoredX + jadeW * (1f - anchorX);
        return !(tRight < estLeft || tLeft > estRight);
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
