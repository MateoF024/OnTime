package com.mateof24.command;

import com.mateof24.manager.TimerManager;
import com.mateof24.timer.Timer;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Handlers for the informational subcommands: list.
 * (help is wired directly to {@link HelpSystem} from the tree.)
 * The command tree itself is registered by {@link TimerCommands}.
 */
final class InfoCommands {

    private InfoCommands() {}

    static int listTimers(CommandContext<CommandSourceStack> ctx) {
        Map<String, Timer> timers = TimerManager.getInstance().getAllTimers();

        if (timers.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("ontime.command.list.empty"), false);
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.translatable("ontime.command.list.header"), false);

        Optional<Timer> activeTimer = TimerManager.getInstance().getActiveTimer();

        for (Timer timer : timers.values()) {
            Component statusComponent = Component.translatable(
                    timer.isRunning() ? "ontime.list.status.running" : "ontime.list.status.stopped"
            );
            String active = activeTimer.isPresent() && activeTimer.get().getName().equals(timer.getName()) ? " §e*" : "";
            String type = timer.isCountUp() ? "↑" : "↓";
            String silent = timer.isSilent() ? " §7[S]" : "";

            String message = String.format("%s §f%s §7%s - §f%s%s%s",
                    statusComponent.getString(), timer.getName(), type, timer.getFormattedTime(), active, silent);

            ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        }

        return 1;
    }
}
