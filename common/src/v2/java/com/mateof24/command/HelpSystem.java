package com.mateof24.command;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

public class HelpSystem {

    private static final int COMMANDS_PER_PAGE = 8;

    /**
     * Definición de un comando de ayuda
     */
    private static class HelpEntry {
        String command;
        String description;
        String usage;
        String[] examples;

        HelpEntry(String command, String description, String usage, String... examples) {
            this.command = command;
            this.description = description;
            this.usage = usage;
            this.examples = examples;
        }
    }

    /**
     * Lista de todos los comandos disponibles
     */
    private static final List<HelpEntry> HELP_ENTRIES = new ArrayList<>();

    static {
        // Comandos básicos
        HELP_ENTRIES.add(new HelpEntry(
                "create",
                "ontime.help.create.desc",
                "/timer create <name> <h> <m> <s> [countUp] [command]",
                "/timer create speedrun 0 30 0 true",
                "/timer create event 1 0 0",
                "/timer create race 0 5 0 false say Race finished!",
                "/timer create custom 0 10 0 true say {name} reached {time}!"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "start",
                "ontime.help.start.desc",
                "/timer start <name>",
                "/timer start speedrun"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "pause",
                "ontime.help.pause.desc",
                "/timer pause",
                "/timer pause"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "stop",
                "ontime.help.stop.desc",
                "/timer stop",
                "/timer stop"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "reset",
                "ontime.help.reset.desc",
                "/timer reset [name]",
                "/timer reset",
                "/timer reset speedrun"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "remove",
                "ontime.help.remove.desc",
                "/timer remove <name>",
                "/timer remove speedrun"
        ));

        // Gestión de tiempo
        HELP_ENTRIES.add(new HelpEntry(
                "set",
                "ontime.help.set.desc",
                "/timer set <name> <hours> <minutes> <seconds>",
                "/timer set speedrun 0 15 0"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "add",
                "ontime.help.add.desc",
                "/timer add <name> <hours> <minutes> <seconds>",
                "/timer add speedrun 0 5 0"
        ));

        // Visualización y sonido
        HELP_ENTRIES.add(new HelpEntry(
                "hide",
                "ontime.help.hide.desc",
                "/timer hide [targets]",
                "/timer hide",
                "/timer hide @a",
                "/timer hide PlayerName"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "silent",
                "ontime.help.silent.desc",
                "/timer silent [targets]",
                "/timer silent",
                "/timer silent @a",
                "/timer silent PlayerName"
        ));

        // Información
        HELP_ENTRIES.add(new HelpEntry(
                "list",
                "ontime.help.list.desc",
                "/timer list",
                "/timer list"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "position",
                "ontime.help.position.desc",
                "/timer position <preset> [targets]",
                "/timer position bossbar",
                "/timer position actionbar @a",
                "/timer position center @p"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "help",
                "ontime.help.help.desc",
                "/timer help [page|command]",
                "/timer help",
                "/timer help 2",
                "/timer help create"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "sound",
                "ontime.help.sound.desc",
                "/timer sound <soundId> [volume] [pitch]",
                "/timer sound block.note_block.hat",
                "/timer sound entity.experience_orb.pickup 0.5",
                "/timer sound ui.button.click 0.8 1.5"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "scale",
                "ontime.help.scale.desc",
                "/timer scale <value>",
                "/timer scale 1.0",
                "/timer scale 1.5",
                "/timer scale 0.8"
        ));

    }

    /**
     * Muestra la página de ayuda general
     */
    public static int showHelpPage(CommandSourceStack source, int page) {
        int totalPages = (int) Math.ceil((double) HELP_ENTRIES.size() / COMMANDS_PER_PAGE);

        // Validar página
        if (page < 1 || page > totalPages) {
            source.sendFailure(Component.translatable("ontime.help.invalid_page", totalPages));
            return 0;
        }

        // Header
        source.sendSuccess(() -> Component.empty(), false);
        source.sendSuccess(() -> Component.literal("§e§l========== §6OnTime Help §e§l==========").withStyle(ChatFormatting.BOLD), false);
        source.sendSuccess(() -> Component.translatable("ontime.help.page", page, totalPages).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.empty(), false);

        // Comandos de esta página
        int startIndex = (page - 1) * COMMANDS_PER_PAGE;
        int endIndex = Math.min(startIndex + COMMANDS_PER_PAGE, HELP_ENTRIES.size());

        for (int i = startIndex; i < endIndex; i++) {
            HelpEntry entry = HELP_ENTRIES.get(i);

            MutableComponent commandComponent = Component.literal("§a/timer " + entry.command)
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent.SuggestCommand("/timer " + entry.command + " "))
                            .withHoverEvent(new HoverEvent.ShowText(Component.translatable("ontime.help.click_to_use")))
                    );

            source.sendSuccess(() -> commandComponent, false);
            source.sendSuccess(() -> Component.literal("  §7" + Component.translatable(entry.description).getString()), false);
        }

        source.sendSuccess(() -> Component.empty(), false);

        // Footer con navegación
        MutableComponent footer = Component.empty();

        if (page > 1) {
            footer.append(Component.literal("§a[< Previous]")
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent.RunCommand("/timer help " + (page - 1)))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Go to page " + (page - 1))))
                    ));
            footer.append(Component.literal(" "));
        }

        if (page < totalPages) {
            footer.append(Component.literal("§a[Next >]")
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent.RunCommand("/timer help " + (page + 1)))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Go to page " + (page + 1))))
                    ));
        }

        source.sendSuccess(() -> footer, false);
        source.sendSuccess(() -> Component.translatable("ontime.help.footer").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);

        return 1;
    }

    /**
     * Muestra ayuda detallada de un comando específico
     */
    public static int showCommandHelp(CommandSourceStack source, String commandName) {
        HelpEntry entry = HELP_ENTRIES.stream()
                .filter(e -> e.command.equalsIgnoreCase(commandName))
                .findFirst()
                .orElse(null);

        if (entry == null) {
            source.sendFailure(Component.translatable("ontime.help.command_not_found", commandName));
            return 0;
        }

        source.sendSuccess(() -> Component.empty(), false);
        source.sendSuccess(() -> Component.literal("§e§l====== §6/timer " + entry.command + " §e§l======").withStyle(ChatFormatting.BOLD), false);
        source.sendSuccess(() -> Component.empty(), false);

        // Descripción
        source.sendSuccess(() -> Component.translatable("ontime.help.description")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(": "))
                .append(Component.translatable(entry.description).withStyle(ChatFormatting.WHITE)), false);

        source.sendSuccess(() -> Component.empty(), false);

        // Uso
        source.sendSuccess(() -> Component.translatable("ontime.help.usage")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(":")), false);

        source.sendSuccess(() -> Component.literal("  §a" + entry.usage), false);

        source.sendSuccess(() -> Component.empty(), false);

        // Ejemplos
        if (entry.examples != null && entry.examples.length > 0) {
            source.sendSuccess(() -> Component.translatable("ontime.help.examples")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(":")), false);

            for (String example : entry.examples) {
                MutableComponent exampleComponent = Component.literal("  §7• §f" + example)
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent.SuggestCommand(example))
                                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("ontime.help.click_to_use")))
                        );
                source.sendSuccess(() -> exampleComponent, false);
            }
        }

        source.sendSuccess(() -> Component.empty(), false);

        return 1;
    }
}