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
                    TimerWebPanel.getInstance().getAccessUrlWithToken()));
            return 0;
        }
        TimerWebPanel.getInstance().start(port, ctx.getSource().getServer());
        if (!TimerWebPanel.getInstance().isRunning()) {
            ctx.getSource().sendFailure(Component.translatable("ontime.command.webpanel.start_failed", port));
            return 0;
        }
        // The tokenised URL is the only way in, so it goes ONLY to whoever ran
        // the command (sendSuccess with broadcast=false) and never to the log.
        String url = TimerWebPanel.getInstance().getAccessUrlWithToken();
        ctx.getSource().sendSuccess(() -> Component.translatable("ontime.command.webpanel.started", url), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("ontime.command.webpanel.token_notice"), false);
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
            String url = TimerWebPanel.getInstance().getAccessUrlWithToken();
            int clients = TimerWebPanel.getInstance().getConnectedClients();
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("ontime.command.webpanel.info", url, clients), false);
        }
        return 1;
    }
}
