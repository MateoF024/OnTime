package com.mateof24.integration;

import com.mateof24.OnTimeConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Optional;

public class FTBQuestsIntegration {

    private static volatile Boolean installed = null;
    private static volatile boolean initialized = false;

    private static Method apiGetMethod;
    private static Method getQuestFileMethod;
    private static Method getQuestObjectMethod;
    private static Method getRewardMethod;
    private static Method getTeamDataMethod;
    private static Method isCompletedMethod;
    private static Method isRewardClaimedMethod;

    public static boolean isInstalled() {
        if (installed == null) {
            try {
                Class.forName("dev.ftb.mods.ftbquests.api.FTBQuestsAPI");
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
            Class<?> apiClass       = Class.forName("dev.ftb.mods.ftbquests.api.FTBQuestsAPI");
            Class<?> teamDataClass  = Class.forName("dev.ftb.mods.ftbquests.quest.TeamData");
            Class<?> rewardClass    = Class.forName("dev.ftb.mods.ftbquests.quest.reward.Reward");
            Class<?> questObjClass  = Class.forName("dev.ftb.mods.ftbquests.quest.QuestObject");
            Class<?> questFileClass = Class.forName("dev.ftb.mods.ftbquests.quest.BaseQuestFile");

            apiGetMethod = apiClass.getMethod("api");
            Object apiObj = apiGetMethod.invoke(null);
            if (apiObj == null) return;

            getQuestFileMethod = apiObj.getClass().getMethod("getQuestFile", boolean.class);
            Object testFile = getQuestFileMethod.invoke(apiObj, false);
            if (testFile == null) return;

            getQuestObjectMethod  = questFileClass.getMethod("get", long.class);
            getRewardMethod       = questFileClass.getMethod("getReward", long.class);
            getTeamDataMethod     = questFileClass.getMethod("getTeamData", net.minecraft.world.entity.player.Player.class);
            isCompletedMethod     = teamDataClass.getMethod("isCompleted", questObjClass);
            isRewardClaimedMethod = teamDataClass.getMethod("isRewardClaimed", java.util.UUID.class, rewardClass);

            initialized = true;
            OnTimeConstants.LOGGER.info("OnTime: FTB Quests integration initialized");
        } catch (Exception e) {
            initialized = false;
            clearMethods();
            OnTimeConstants.LOGGER.debug("OnTime: FTB Quests init deferred: {}", e.getMessage());
        }
    }

    private static void clearMethods() {
        apiGetMethod = null;
        getQuestFileMethod = null;
        getQuestObjectMethod = null;
        getRewardMethod = null;
        getTeamDataMethod = null;
        isCompletedMethod = null;
        isRewardClaimedMethod = null;
    }

    public static boolean isReady() {
        return initialized
                && apiGetMethod != null && getQuestFileMethod != null
                && getQuestObjectMethod != null && getRewardMethod != null
                && getTeamDataMethod != null && isCompletedMethod != null
                && isRewardClaimedMethod != null;
    }

    private static Object getServerFile() {
        try {
            Object api = apiGetMethod.invoke(null);
            if (api == null) return null;
            return getQuestFileMethod.invoke(api, false);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isQuestCompletedByAnyPlayer(MinecraftServer server, String hexId) {
        if (!isReady()) { tryInit(); return false; }
        try {
            long id  = Long.parseUnsignedLong(hexId.trim(), 16);
            Object sqf = getServerFile();
            if (sqf == null) return false;
            Object quest = getQuestObjectMethod.invoke(sqf, id);
            if (quest == null) return false;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                @SuppressWarnings("unchecked")
                Optional<Object> team = (Optional<Object>) getTeamDataMethod.invoke(sqf, player);
                if (team == null || team.isEmpty()) continue;
                if ((boolean) isCompletedMethod.invoke(team.get(), quest)) return true;
            }
        } catch (NumberFormatException e) {
            OnTimeConstants.LOGGER.warn("OnTime: Invalid FTB quest ID (hex): {}", hexId);
        } catch (Exception e) {
            OnTimeConstants.LOGGER.debug("OnTime: FTB quest check error: {}", e.getMessage());
        }
        return false;
    }

    public static boolean isRewardClaimedByAnyPlayer(MinecraftServer server, String hexId) {
        if (!isReady()) { tryInit(); return false; }
        try {
            long id  = Long.parseUnsignedLong(hexId.trim(), 16);
            Object sqf = getServerFile();
            if (sqf == null) return false;
            Object reward = getRewardMethod.invoke(sqf, id);
            if (reward == null) return false;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                @SuppressWarnings("unchecked")
                Optional<Object> team = (Optional<Object>) getTeamDataMethod.invoke(sqf, player);
                if (team == null || team.isEmpty()) continue;
                if ((boolean) isRewardClaimedMethod.invoke(team.get(), player.getUUID(), reward)) return true;
            }
        } catch (NumberFormatException e) {
            OnTimeConstants.LOGGER.warn("OnTime: Invalid FTB reward ID (hex): {}", hexId);
        } catch (Exception e) {
            OnTimeConstants.LOGGER.debug("OnTime: FTB reward check error: {}", e.getMessage());
        }
        return false;
    }

    public static void invalidate() {
        installed = null;
        initialized = false;
        clearMethods();
    }
}