package com.mateof24.integration;

import com.mateof24.OnTimeConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.UUID;

public class FTBQuestsIntegration {

    private static volatile Boolean installed = null;
    private static volatile boolean initialized = false;

    private static Method apiMethod;
    private static Method getQuestFileMethod;
    private static Method getRewardMethod;
    private static Method getQuestObjectMethod;
    private static Method getNullableTeamDataMethod;
    private static Method isCompletedMethod;
    private static Method isRewardClaimedMethod;

    public static boolean isInstalled() {
        if (installed == null) {
            try {
                Class.forName("dev.ftb.mods.ftbquests.api.FTBQuestsAPI");
                installed = true;
                OnTimeConstants.LOGGER.info("[OnTime/FTBQuests] FTB Quests detected");
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

            apiMethod = apiClass.getMethod("api");
            Object apiObj = apiMethod.invoke(null);
            if (apiObj == null) throw new Exception("FTBQuestsAPI.api() returned null");

            getQuestFileMethod        = apiObj.getClass().getMethod("getQuestFile", boolean.class);
            getQuestObjectMethod      = questFileClass.getMethod("get", long.class);
            getRewardMethod           = questFileClass.getMethod("getReward", long.class);
            getNullableTeamDataMethod = questFileClass.getMethod("getNullableTeamData", UUID.class);
            isCompletedMethod         = teamDataClass.getMethod("isCompleted", questObjClass);
            isRewardClaimedMethod     = teamDataClass.getMethod("isRewardClaimed", UUID.class, rewardClass);

            Object testFile = getQuestFileMethod.invoke(apiObj, false);
            if (testFile == null) throw new Exception("getQuestFile(false) returned null");

            initialized = true;
            OnTimeConstants.LOGGER.info("[OnTime/FTBQuests] Reflection initialized successfully");
        } catch (Exception e) {
            initialized = false;
            clearMethods();
            OnTimeConstants.LOGGER.debug("[OnTime/FTBQuests] Init deferred: {}", e.getMessage());
        }
    }

    private static void clearMethods() {
        apiMethod = null;
        getQuestFileMethod = null;
        getRewardMethod = null;
        getQuestObjectMethod = null;
        getNullableTeamDataMethod = null;
        isCompletedMethod = null;
        isRewardClaimedMethod = null;
    }

    public static boolean isReady() {
        return initialized
                && apiMethod != null && getQuestFileMethod != null
                && getQuestObjectMethod != null && getRewardMethod != null
                && getNullableTeamDataMethod != null && isCompletedMethod != null
                && isRewardClaimedMethod != null;
    }

    private static Object getServerFile() {
        if (!isReady()) return null;
        try {
            Object api = apiMethod.invoke(null);
            if (api == null) return null;
            return getQuestFileMethod.invoke(api, false);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object getTeamData(Object sqf, UUID uuid) throws Exception {
        return getNullableTeamDataMethod.invoke(sqf, uuid);
    }

    public static boolean hasPlayerCompletedQuest(ServerPlayer player, String hexId) {
        if (!isReady()) return false;
        try {
            long id = Long.parseUnsignedLong(hexId.trim(), 16);
            Object sqf = getServerFile();
            if (sqf == null) return false;
            Object quest = getQuestObjectMethod.invoke(sqf, id);
            if (quest == null) return false;
            Object team = getTeamData(sqf, player.getUUID());
            if (team == null) return false;
            return (boolean) isCompletedMethod.invoke(team, quest);
        } catch (NumberFormatException e) {
            return false;
        } catch (Exception e) {
            OnTimeConstants.LOGGER.debug("[OnTime/FTBQuests] hasPlayerCompletedQuest error: {}", e.getMessage());
            return false;
        }
    }

    public static boolean hasPlayerClaimedReward(ServerPlayer player, String hexId) {
        if (!isReady()) return false;
        try {
            long id = Long.parseUnsignedLong(hexId.trim(), 16);
            Object sqf = getServerFile();
            if (sqf == null) return false;
            Object reward = getRewardMethod.invoke(sqf, id);
            if (reward == null) return false;
            Object team = getTeamData(sqf, player.getUUID());
            if (team == null) return false;
            return (boolean) isRewardClaimedMethod.invoke(team, player.getUUID(), reward);
        } catch (NumberFormatException e) {
            return false;
        } catch (Exception e) {
            OnTimeConstants.LOGGER.debug("[OnTime/FTBQuests] hasPlayerClaimedReward error: {}", e.getMessage());
            return false;
        }
    }

    public static boolean isQuestCompletedByAnyPlayer(MinecraftServer server, String hexId) {
        if (!isReady()) { tryInit(); return false; }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (hasPlayerCompletedQuest(p, hexId)) return true;
        }
        return false;
    }

    public static boolean isRewardClaimedByAnyPlayer(MinecraftServer server, String hexId) {
        if (!isReady()) { tryInit(); return false; }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (hasPlayerClaimedReward(p, hexId)) return true;
        }
        return false;
    }

    public static void invalidate() {
        installed = null;
        initialized = false;
        clearMethods();
    }
}
