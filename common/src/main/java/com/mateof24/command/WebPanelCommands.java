package com.mateof24.command;

import com.mateof24.webpanel.TimerWebPanel;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * Handlers for the webpanel subcommands: start / stop / info.
 * The command tree itself is registered by {@link TimerCommands}.
 */
final class WebPanelCommands {

    private WebPanelCommands() {}

    static int webPanelStart(CommandContext<CommandSourceStack> ctx, int port) {
        if (TimerWebPanel.getInstance().isRunning()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.webpanel.already_running",
                    TimerWebPanel.getInstance().getAccessUrl()));
            return 0;
        }
        TimerWebPanel.getInstance().start(port, ctx.getSource().getServer());
        if (!TimerWebPanel.getInstance().isRunning()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.webpanel.start_failed", port));
            return 0;
        }
        String url = TimerWebPanel.getInstance().getAccessUrl();
        ctx.getSource().sendSuccess(() -> Component.translatable("ontime.command.webpanel.started", url), false);
        return 1;
    }

    static int webPanelStop(CommandContext<CommandSourceStack> ctx) {
        if (!TimerWebPanel.getInstance().isRunning()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.webpanel.not_running"));
            return 0;
        }
        TimerWebPanel.getInstance().stop();
        ctx.getSource().sendSuccess(() -> Component.translatable("ontime.command.webpanel.stopped"), true);
        return 1;
    }

    static int webPanelInfo(CommandContext<CommandSourceStack> ctx) {
        if (!TimerWebPanel.getInstance().isRunning()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("ontime.command.webpanel.not_running"), false);
        } else {
            String url = TimerWebPanel.getInstance().getAccessUrl();
            int clients = TimerWebPanel.getInstance().getConnectedClients();
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.webpanel.info", url, clients), false);
        }
        return 1;
    }
}
