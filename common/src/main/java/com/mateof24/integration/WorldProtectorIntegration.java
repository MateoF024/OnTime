package com.mateof24.integration;

import com.mateof24.OnTimeConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class WorldProtectorIntegration {

    private static volatile Boolean installed = null;
    private static volatile boolean initialized = false;

    private static Object managerInstance;
    private static Method getDimRegionsMethod;
    private static Method getAreaMethod;
    private static Method containsPositionMethod;

    public static boolean isInstalled() {
        if (installed == null) {
            try {
                Class.forName("de.z0rdak.yawp.WorldProtector");
                installed = true;
            } catch (ClassNotFoundException e) {
                installed = false;
            }
        }
        return installed;
    }

    public static synchronized void tryInit() {
        if (initialized || !isInstalled()) return;
        try {
            Class<?> managerClass = Class.forName("de.z0rdak.yawp.core.region.RegionDataManager");

            try {
                Field instanceField = managerClass.getField("INSTANCE");
                managerInstance = instanceField.get(null);
            } catch (NoSuchFieldException ignored) {
                Method getMethod = managerClass.getMethod("get");
                managerInstance = getMethod.invoke(null);
            }

            if (managerInstance == null) return;

            for (Method m : managerInstance.getClass().getMethods()) {
                String name = m.getName();
                if ((name.equals("getRegionsForDim") || name.equals("getDimRegions") || name.equals("getRegions"))
                        && m.getParameterCount() == 1) {
                    getDimRegionsMethod = m;
                    break;
                }
            }

            Class<?> areaClass;
            try {
                areaClass = Class.forName("de.z0rdak.yawp.core.area.AbstractArea");
            } catch (ClassNotFoundException e) {
                areaClass = Class.forName("de.z0rdak.yawp.core.area.IProtectedRegionArea");
            }
            containsPositionMethod = areaClass.getMethod("containsPosition", BlockPos.class);

            Class<?> regionClass;
            try {
                regionClass = Class.forName("de.z0rdak.yawp.core.region.AbstractMarkableRegion");
            } catch (ClassNotFoundException e) {
                regionClass = Class.forName("de.z0rdak.yawp.core.region.IMarkableRegion");
            }

            for (Method m : regionClass.getMethods()) {
                if (m.getName().equals("getArea") && m.getParameterCount() == 0) {
                    getAreaMethod = m;
                    break;
                }
            }

            if (getDimRegionsMethod != null && getAreaMethod != null && containsPositionMethod != null) {
                initialized = true;
                OnTimeConstants.LOGGER.info("OnTime: YAWP integration initialized");
            }
        } catch (Exception e) {
            initialized = false;
            managerInstance = null;
            getDimRegionsMethod = null;
            getAreaMethod = null;
            containsPositionMethod = null;
            OnTimeConstants.LOGGER.debug("OnTime: YAWP init deferred: {}", e.getMessage());
        }
    }

    public static boolean isAnyPlayerInRegion(MinecraftServer server, String regionId) {
        if (!isReady()) { tryInit(); return false; }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isPlayerInRegion(player, regionId)) return true;
        }
        return false;
    }

    private static boolean isPlayerInRegion(ServerPlayer player, String regionId) {
        try {
            ServerLevel level = player.serverLevel();
            Object dimRegions = getDimRegionsMethod.invoke(managerInstance, level.dimension());
            if (dimRegions == null) return false;

            Map<?, ?> regionsMap = null;
            if (dimRegions instanceof Map<?, ?> m) {
                regionsMap = m;
            } else {
                for (Method method : dimRegions.getClass().getMethods()) {
                    if ((method.getName().equals("entrySet") || method.getName().equals("getRegions"))
                            && method.getParameterCount() == 0) {
                        Object result = method.invoke(dimRegions);
                        if (result instanceof Map<?, ?> rm) { regionsMap = rm; break; }
                    }
                }
            }

            if (regionsMap == null) return false;

            Object region = regionsMap.get(regionId);
            if (region == null) return false;

            Object area = getAreaMethod.invoke(region);
            if (area == null) return false;

            return (boolean) containsPositionMethod.invoke(area, player.blockPosition());
        } catch (Exception e) {
            OnTimeConstants.LOGGER.debug("OnTime: YAWP region check error: {}", e.getMessage());
            return false;
        }
    }

    public static List<String> getRegionNames(MinecraftServer server) {
        if (!isReady()) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        try {
            for (ServerLevel level : server.getAllLevels()) {
                Object dimRegions = getDimRegionsMethod.invoke(managerInstance, level.dimension());
                if (dimRegions == null) continue;
                if (dimRegions instanceof Map<?, ?> map) {
                    map.keySet().forEach(k -> names.add(String.valueOf(k)));
                }
            }
        } catch (Exception e) {
            OnTimeConstants.LOGGER.debug("OnTime: YAWP getRegionNames error: {}", e.getMessage());
        }
        return names;
    }

    public static boolean isReady() {
        return initialized && managerInstance != null
                && getDimRegionsMethod != null && getAreaMethod != null && containsPositionMethod != null;
    }
}