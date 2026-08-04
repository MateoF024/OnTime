package com.mateof24.platform;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.nio.file.Path;

public interface IPlatformHelper {
    String getPlatformName();
    boolean isModLoaded(String modId);
    Path getConfigDir();
    /** Pushes each online player the executions their audience covers. */
    void sendTimerState(MinecraftServer server);

    /** Pushes one player their own view, for joins. */
    void sendTimerState(ServerPlayer player);
    void sendVisibilityPacket(ServerPlayer player, boolean visible);
    void sendSilentPacket(ServerPlayer player, boolean silent);
    default void sendDisplayConfigPacket(ServerPlayer player) {}
    default void sendDisplayConfigPacketToAll(MinecraftServer server) {}
    void registerPackets();
    boolean checkScoreboardCondition(MinecraftServer server, String objective, int score, String target);
    void updateScoreboardTimer(MinecraftServer server, String timerName, long currentSeconds, long targetSeconds);
    void clearScoreboardTimer(MinecraftServer server);
    default long getScoreboardValue(MinecraftServer server, String objective, String holder) { return 0; }
}