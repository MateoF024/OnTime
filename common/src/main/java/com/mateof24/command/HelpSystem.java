package com.mateof24.command;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/*
 * The version-specific part lives in HelpStyle (common/src/v1 and v2): the
 * ClickEvent/HoverEvent API changed shape in 1.21.5, and that was the only
 * reason this whole class used to be duplicated.
 *
 * The legacy § codes in the decorative headers are kept deliberately: they
 * rely on the color-code style reset, are still fully supported through 26.2,
 * and replicating them with Style would change the rendered output. Revisit
 * only if Mojang actually removes § parsing.
 */
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
                "/timer start <name> [targets] [shared|each]",
                "/timer start speedrun",
                "/timer start speedrun @a[team=red]",
                "/timer start speedrun @a each"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "pause",
                "ontime.help.pause.desc",
                "/timer pause [name] [targets]",
                "/timer pause",
                "/timer pause speedrun",
                "/timer pause speedrun @a[team=red]"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "resume",
                "ontime.help.resume.desc",
                "/timer resume [name] [targets]",
                "/timer resume",
                "/timer resume speedrun"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "stop",
                "ontime.help.stop.desc",
                "/timer stop [name] [targets]",
                "/timer stop",
                "/timer stop speedrun",
                "/timer stop speedrun Bob"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "reset",
                "ontime.help.reset.desc",
                "/timer reset [name] [targets]",
                "/timer reset",
                "/timer reset speedrun"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "confirm",
                "ontime.help.confirm.desc",
                "/timer confirm",
                "/timer confirm"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "audience",
                "ontime.help.audience.desc",
                "/timer audience <name> <list|add|remove> [targets]",
                "/timer audience speedrun list",
                "/timer audience speedrun add Bob",
                "/timer audience speedrun remove @a[team=red]"
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
                "/timer hide [targets] [show|hide|toggle]",
                "/timer hide",
                "/timer hide @a hide",
                "/timer hide PlayerName show"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "silent",
                "ontime.help.silent.desc",
                "/timer silent [targets] [mute|unmute|toggle]",
                "/timer silent",
                "/timer silent @a mute",
                "/timer silent PlayerName unmute"
        ));

        // Información
        HELP_ENTRIES.add(new HelpEntry(
                "list",
                "ontime.help.list.desc",
                "/timer list",
                "/timer list"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "gui",
                "ontime.help.gui.desc",
                "/timer gui",
                "/timer gui"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "status",
                "ontime.help.status.desc",
                "/timer status <name>",
                "/timer status speedrun"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "position",
                "ontime.help.position.desc",
                "/timer position <default|timer> <preset|clear> [x] [y]",
                "/timer position default bossbar",
                "/timer position speedrun top_left",
                "/timer position speedrun custom 40 80",
                "/timer position speedrun clear",
                "/timer position bossbar"
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
                "/timer scale <default|timer> <value|clear>",
                "/timer scale default 1.0",
                "/timer scale speedrun 1.5",
                "/timer scale speedrun clear",
                "/timer scale 0.8"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "command",
                "ontime.help.command.desc",
                "/timer command <name> [command]",
                "/timer command speedrun",
                "/timer command speedrun say {name} finished in {time}!",
                "/timer command event title @a [{\"text\":\"\"}] [{\"text\":\"Event over!\"}]"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "commands",
                "ontime.help.commands.desc",
                "/timer commands <name> [add <h> <m> <s> <cmd>|add finish <cmd>|list|remove <index>|clear]",
                "/timer commands event add 0 0 10 say 10 seconds left!",
                "/timer commands event add finish say The event is over!",
                "/timer commands event list",
                "/timer commands event remove 2"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "title",
                "ontime.help.title.desc",
                "/timer title <name> [above|below|left|right] [text|clear]",
                "/timer title event above Boss Fight",
                "/timer title event below {\"text\":\"Hurry up!\",\"color\":\"red\",\"bold\":true}",
                "/timer title event above clear",
                "/timer title event clear"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "repeat",
                "ontime.help.repeat.desc",
                "/timer repeat <name> [count|-1] [cooldownSeconds]",
                "/timer repeat speedrun",
                "/timer repeat event 3",
                "/timer repeat event 0",
                "/timer repeat event -1 30",
                "/timer repeat event 5 10"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "sequence",
                "ontime.help.sequence.desc",
                "/timer sequence <name> [nextName|clear] [cooldownSeconds]",
                "/timer sequence round1 round2",
                "/timer sequence round1 round2 30",
                "/timer sequence round1",
                "/timer sequence round1 clear"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "condition",
                "ontime.help.condition.desc",
                "/timer condition <name> <objective> <score> [target|clear]",
                "/timer condition event kills 10",
                "/timer condition event kills 10 PlayerName",
                "/timer condition event clear"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "trigger",
                "ontime.help.trigger.desc",
                "/timer trigger <name> <event> [action|clear]",
                "/timer trigger event player_death",
                "/timer trigger event dimension_change minecraft:the_nether start",
                "/timer trigger event clear"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "expr",
                "ontime.help.expr.desc",
                "/timer expr <create|set|add> <name> <expression>",
                "/timer expr create round 60 * players_online",
                "/timer expr set round 300",
                "/timer expr add round 30"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "export",
                "ontime.help.export.desc",
                "/timer export <name>",
                "/timer export speedrun",
                "/timer export event"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "import",
                "ontime.help.import.desc",
                "/timer import <filename> [newname]",
                "/timer import speedrun",
                "/timer import speedrun speedrun_copy"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "clone",
                "ontime.help.clone.desc",
                "/timer clone <source> <dest>",
                "/timer clone speedrun speedrun2",
                "/timer clone event event_backup"
        ));

        HELP_ENTRIES.add(new HelpEntry(
                "webpanel",
                "ontime.help.webpanel.desc",
                "/timer webpanel <start|stop|info> [port]",
                "/timer webpanel start",
                "/timer webpanel start 9000",
                "/timer webpanel info",
                "/timer webpanel stop"
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
                    .withStyle(style -> HelpStyle.suggest(style, "/timer " + entry.command + " ",
                            Component.translatable("ontime.help.click_to_use")));

            source.sendSuccess(() -> commandComponent, false);
            source.sendSuccess(() -> Component.literal("  §7" + Component.translatable(entry.description).getString()), false);
        }

        source.sendSuccess(() -> Component.empty(), false);

        // Footer con navegación
        MutableComponent footer = Component.empty();

        if (page > 1) {
            footer.append(Component.literal("§a[< Previous]")
                    .withStyle(style -> HelpStyle.run(style, "/timer help " + (page - 1),
                            Component.literal("Go to page " + (page - 1)))));
            footer.append(Component.literal(" "));
        }

        if (page < totalPages) {
            footer.append(Component.literal("§a[Next >]")
                    .withStyle(style -> HelpStyle.run(style, "/timer help " + (page + 1),
                            Component.literal("Go to page " + (page + 1)))));
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
                        .withStyle(style -> HelpStyle.suggest(style, example,
                                Component.translatable("ontime.help.click_to_use")));
                source.sendSuccess(() -> exampleComponent, false);
            }
        }

        source.sendSuccess(() -> Component.empty(), false);

        return 1;
    }
}