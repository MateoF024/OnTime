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
    private static final int SYNC_INTERVAL = 20; // Sync every second

    // Tick handler called every server tick
    public static void tick(MinecraftServer server) {
        Optional<Timer> activeTimerOpt = TimerManager.getInstance().getActiveTimer();

        if (activeTimerOpt.isEmpty()) {
            return;
        }

        Timer activeTimer = activeTimerOpt.get();

        if (!activeTimer.isRunning()) {
            return;
        }

        // Tick the timer and check if it finished
        boolean finished = activeTimer.tick();

        // Sync to clients periodically
        syncCounter++;
        if (syncCounter >= SYNC_INTERVAL) {
            syncCounter = 0;
            syncTimerToClients(server, activeTimer);
        }

        // Handle timer completion
        if (finished) {
            executeTimerCommand(server, activeTimer);
            TimerManager.getInstance().clearActiveTimer();
            TimerManager.getInstance().saveTimers();

            // Clear on clients
            NetworkHandler.syncTimerToClients(server, "", 0, 0, false, false);
        }
    }

    // Sync timer state to all clients
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

    // Execute timer command with max privileges
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
                    4, // Max permission level
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