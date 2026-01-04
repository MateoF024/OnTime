package com.mateof24.tick;

import com.mateof24.manager.TimerManager;
import com.mateof24.network.NetworkHandler;
import com.mateof24.timer.Timer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class TimerTickHandler {
    private static int syncCounter = 0;
    private static final int SYNC_INTERVAL = 20;

    public static void tick(MinecraftServer server) {
        Optional<Timer> activeTimerOpt = TimerManager.getInstance().getActiveTimer();

        if (activeTimerOpt.isEmpty()) {
            return;
        }

        Timer activeTimer = activeTimerOpt.get();

        if (!activeTimer.isRunning()) {
            return;
        }

        boolean finished = activeTimer.tick();

        syncCounter++;
        if (syncCounter >= SYNC_INTERVAL) {
            syncCounter = 0;
            syncTimerToClients(server, activeTimer);
        }

        if (finished) {
            executeTimerCommand(server, activeTimer);
            activeTimer.reset();
            TimerManager.getInstance().clearActiveTimer();
            TimerManager.getInstance().saveTimers();
            NetworkHandler.syncTimerToClients(server, "", 0, 0, false, false);
        }
    }

    private static void syncTimerToClients(MinecraftServer server, Timer timer) {
        NetworkHandler.syncTimerToClients(
                server,
                timer.getName(),
                timer.getCurrentTicks(),
                timer.getTargetTicks(),
                timer.isCountUp(),
                timer.isRunning()
        );
    }

    private static void executeTimerCommand(MinecraftServer server, Timer timer) {
        String command = timer.getCommand();

        if (command == null || command.trim().isEmpty()) {
            return;
        }

        try {
            ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
            if (overworld == null) return;

            CommandSourceStack source = new CommandSourceStack(
                    server,
                    Vec3.ZERO,
                    Vec2.ZERO,
                    overworld,
                    4,
                    "OnTime",
                    net.minecraft.network.chat.Component.literal("OnTime"),
                    server,
                    null
            );

            server.getCommands().performPrefixedCommand(source, command);
        } catch (Exception e) {
            com.mateof24.OnTime.LOGGER.error("Failed to execute timer command: " + command, e);
        }
    }
}