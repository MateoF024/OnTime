package com.mateof24.command;

import com.mateof24.api.RunMode;

import com.mateof24.compat.VanillaCompat;
import com.mateof24.config.ModConfig;
import com.mateof24.config.TimerPositionPreset;
import com.mateof24.manager.TimerManager;
import com.mateof24.permission.PermissionHelper;
import com.mateof24.permission.PermissionNodes;
import com.mateof24.storage.TimerStorage;
import com.mateof24.timer.Timer;
import com.mateof24.trigger.Trigger;
import com.mateof24.trigger.Who;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;

import java.util.Map;

/**
 * Registers the {@code /timer} command tree. The subcommand handlers live in
 * the per-area classes of this package ({@link LifecycleCommands},
 * {@link DisplayCommands}, {@link BehaviorCommands}, {@link SharingCommands},
 * {@link WebPanelCommands}, {@link InfoCommands}); this class only owns the
 * tree topology, the shared suggestion providers and small shared helpers.
 */
public class TimerCommands {

    private static class TimerNameSuggestionProvider implements com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> {
        @Override
        public java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> getSuggestions(
                CommandContext<CommandSourceStack> context,
                com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {

            // No defensive copy: this runs on the server thread once per
            // keystroke, and only names are read.
            String remaining = builder.getRemaining().toLowerCase();

            // Prefixes first, then anything containing what was typed, the way
            // vanilla finds an id from the middle of it. Matching only on the
            // prefix meant you had to remember how a timer's name began.
            java.util.List<String> contains = new java.util.ArrayList<>();
            for (Timer timer : TimerManager.getInstance().timersView()) {
                String lower = timer.getName().toLowerCase();
                if (lower.startsWith(remaining)) builder.suggest(timer.getName());
                else if (!remaining.isEmpty() && lower.contains(remaining)) contains.add(timer.getName());
            }
            contains.forEach(builder::suggest);

            return builder.buildFuture();
        }
    }

    /**
     * One position branch of /timer title: <pos> clear | <pos> <text...>.
     * The 'clear' literal wins over the greedy text (Brigadier priority) —
     * a literal title saying "clear" needs the JSON form {"text":"clear"}.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> titlePosition(String position) {
        return Commands.literal(position)
                .then(Commands.literal("clear")
                        .executes(ctx -> BehaviorCommands.clearTitle(ctx, position)))
                .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(ctx -> BehaviorCommands.setTitle(ctx, position,
                                StringArgumentType.getString(ctx, "text"))));
    }

    /** A run-scoped lifecycle handler: {@code /timer <verb> [<name>] [<targets>]}. */
    @FunctionalInterface
    private interface SelectionCommand {
        int run(CommandContext<CommandSourceStack> ctx, String name,
                java.util.Collection<net.minecraft.server.level.ServerPlayer> targets)
                throws com.mojang.brigadier.exceptions.CommandSyntaxException;
    }

    /**
     * Builds one of the four lifecycle branches, all of which share the same
     * shape: no argument (every run), a timer name, or a name and a selector.
     *
     * <p>The name argument is declared before the bare selector one on purpose.
     * Brigadier tries argument children in declaration order, so a plain word
     * means the timer of that name; {@code @}-selectors fail the word parse and
     * fall through to the selector branch, which is what makes
     * {@code /timer stop @a} work without making {@code /timer stop race}
     * ambiguous.</p>
     */
    private static LiteralArgumentBuilder<CommandSourceStack> selection(
            String verb, String permission,
            com.mojang.brigadier.Command<CommandSourceStack> bare,
            SelectionCommand selective) {
        return Commands.literal(verb)
                .requires(source -> PermissionHelper.hasPermission(source, permission, 4))
                // "all" is spelled out. The bare verb used to mean the 4.0.0
                // toggle, so /timer pause could resume something.
                .then(Commands.literal("all").executes(bare))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(TIMER_SUGGESTIONS)
                        .executes(ctx -> selective.run(ctx, StringArgumentType.getString(ctx, "name"), null))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> selective.run(ctx,
                                        StringArgumentType.getString(ctx, "name"),
                                        EntityArgument.getPlayers(ctx, "targets")))
                        )
                )
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> selective.run(ctx, null, EntityArgument.getPlayers(ctx, "targets")))
                );
    }

    static String formatTime(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static final TimerNameSuggestionProvider TIMER_SUGGESTIONS = new TimerNameSuggestionProvider();

    // ---- /timer trigger <name> add ... ----
    //
    // Every kind ends in the same two literals. The action used to be a
    // separate argument on some kinds, a literal baked into the node name on
    // others (if / if_start), and absent on the scoreboard branch, which could
    // only ever end a timer.

    /** Builds the trigger once the arguments and the subject are both known. */
    @FunctionalInterface
    private interface TriggerFactory {
        Trigger build(CommandContext<CommandSourceStack> ctx, Who who);
    }

    /**
     * One action leaf, and everything that can follow it.
     *
     * <p>Bare, it means the default subject — the timer's own audience, any
     * one of them — which is what every trigger meant before there was a
     * choice. Spelling out a quantifier and a subject after it is what makes
     * "when both of them do" a different thing from "when either does".</p>
     */
    private static LiteralArgumentBuilder<CommandSourceStack> actionNode(
            String literal, TriggerFactory make) {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(literal)
                .executes(ctx -> BehaviorCommands.addTrigger(ctx, make.build(ctx, Who.DEFAULT)));

        for (Who.Quantifier quantifier : Who.Quantifier.values()) {
            if (quantifier == Who.Quantifier.AT_LEAST) {
                node = node.then(Commands.literal(quantifier.lower())
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .then(scopeNodes(make, quantifier, true))
                                .then(scopeValueNodes(make, quantifier, true))));
            } else {
                node = node.then(Commands.literal(quantifier.lower())
                        .then(scopeNodes(make, quantifier, false))
                        .then(scopeValueNodes(make, quantifier, false)));
            }
        }
        return node;
    }

    /** The two subjects that name nobody: they are complete on their own. */
    private static LiteralArgumentBuilder<CommandSourceStack> scopeNodes(
            TriggerFactory make, Who.Quantifier quantifier, boolean counted) {
        LiteralArgumentBuilder<CommandSourceStack> first = null;
        for (Who.Scope scope : Who.Scope.values()) {
            if (scope.needsValue()) continue;
            LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(scope.lower())
                    .executes(ctx -> BehaviorCommands.addTrigger(ctx, make.build(ctx,
                            new Who(scope, "", quantifier, counted
                                    ? IntegerArgumentType.getInteger(ctx, "count") : 1))));
            first = first == null ? node : first.then(node);
        }
        return first;
    }

    /**
     * The two that do name somebody, both greedy and both last.
     *
     * <p>A list of names has commas in it and a selector has brackets, so
     * neither is a word. Greedy is also why they cannot be followed by
     * anything.</p>
     */
    private static LiteralArgumentBuilder<CommandSourceStack> scopeValueNodes(
            TriggerFactory make, Who.Quantifier quantifier, boolean counted) {
        LiteralArgumentBuilder<CommandSourceStack> first = null;
        for (Who.Scope scope : Who.Scope.values()) {
            if (!scope.needsValue()) continue;
            LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(scope.lower())
                    .then(Commands.argument("who", StringArgumentType.greedyString())
                            .suggests(scope == Who.Scope.PLAYERS
                                    ? TimerCommands::suggestPlayerNames : TimerCommands::suggestSelectors)
                            .executes(ctx -> BehaviorCommands.addTrigger(ctx, make.build(ctx,
                                    new Who(scope, StringArgumentType.getString(ctx, "who"), quantifier,
                                            counted ? IntegerArgumentType.getInteger(ctx, "count") : 1)))));
            first = first == null ? node : first.then(node);
        }
        return first;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> bareTrigger(String literal, Trigger.Kind kind) {
        return Commands.literal(literal)
                .then(actionNode("start", (c, who) -> Trigger.of(kind, Trigger.Action.START, "", who)))
                .then(actionNode("finish", (c, who) -> Trigger.of(kind, Trigger.Action.FINISH, "", who)));
    }

    /** A kind narrowed by a resource id, with completion for it. */
    private static LiteralArgumentBuilder<CommandSourceStack> idTrigger(
            String literal, Trigger.Kind kind,
            com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> suggestions) {
        return Commands.literal(literal)
                .then(Commands.argument("id", VanillaCompat.idArgument())
                        .suggests(suggestions)
                        .then(actionNode("start", (c, who) -> Trigger.of(kind, Trigger.Action.START,
                                VanillaCompat.getIdArgument(c, "id"), who)))
                        .then(actionNode("finish", (c, who) -> Trigger.of(kind, Trigger.Action.FINISH,
                                VanillaCompat.getIdArgument(c, "id"), who))));
    }

    /** FTB ids are opaque hex strings, so there is nothing to complete. */
    private static LiteralArgumentBuilder<CommandSourceStack> wordTrigger(String literal, Trigger.Kind kind) {
        return Commands.literal(literal)
                .then(Commands.argument("id", StringArgumentType.word())
                        .then(actionNode("start", (c, who) -> Trigger.of(kind, Trigger.Action.START,
                                StringArgumentType.getString(c, "id"), who)))
                        .then(actionNode("finish", (c, who) -> Trigger.of(kind, Trigger.Action.FINISH,
                                StringArgumentType.getString(c, "id"), who))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> scoreboardAction(String literal, Trigger.Action action) {
        return actionNode(literal, (c, who) -> Trigger.scoreboard(action,
                StringArgumentType.getString(c, "objective"),
                IntegerArgumentType.getInteger(c, "score"), who));
    }

    /**
     * The expression takes no subject.
     *
     * <p>It is one question asked of the server, not of a player, so there is
     * nobody for a quantifier to count. Everything a per-player condition needs
     * belongs in the expression itself.</p>
     */
    private static LiteralArgumentBuilder<CommandSourceStack> expressionAction(String literal, Trigger.Action action) {
        return Commands.literal(literal)
                .then(Commands.argument("expression", StringArgumentType.greedyString())
                        .executes(ctx -> BehaviorCommands.addTrigger(ctx,
                                Trigger.expression(action, StringArgumentType.getString(ctx, "expression")))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPlayerNames(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        net.minecraft.server.MinecraftServer server = ctx.getSource().getServer();
        if (server == null) return builder.buildFuture();
        String remaining = builder.getRemaining().toLowerCase();
        // After a comma the completion is for the next name, not the whole list.
        int comma = remaining.lastIndexOf(',');
        String head = comma < 0 ? "" : remaining.substring(0, comma + 1);
        String tail = comma < 0 ? remaining : remaining.substring(comma + 1).trim();
        for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getScoreboardName().toLowerCase().startsWith(tail)) {
                builder.suggest(head + player.getScoreboardName());
            }
        }
        return builder.buildFuture();
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestSelectors(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        for (String option : new String[]{"@a", "@a[team=]", "@a[tag=]", "@a[gamemode=survival]"}) {
            if (option.startsWith(remaining)) builder.suggest(option);
        }
        return builder.buildFuture();
    }

    /**
     * Every advancement the server knows.
     *
     * <p>There were none: the id argument was left bare, so the one trigger
     * kind whose value nobody can guess was the one you had to type blind.</p>
     */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestAdvancements(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        net.minecraft.server.MinecraftServer server = ctx.getSource().getServer();
        if (server == null) return builder.buildFuture();
        String remaining = builder.getRemaining().toLowerCase();
        java.util.List<String> contains = new java.util.ArrayList<>();
        for (net.minecraft.advancements.AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
            String id = holder.id().toString();
            String lower = id.toLowerCase();
            if (lower.startsWith(remaining)) builder.suggest(id);
            else if (!remaining.isEmpty() && lower.contains(remaining)) contains.add(id);
        }
        contains.forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestObjectives(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        net.minecraft.server.MinecraftServer server = ctx.getSource().getServer();
        if (server == null) return builder.buildFuture();
        String remaining = builder.getRemaining().toLowerCase();
        server.getScoreboard().getObjectives().forEach(objective -> {
            if (objective.getName().toLowerCase().startsWith(remaining)) builder.suggest(objective.getName());
        });
        return builder.buildFuture();
    }

    /** Any holder, plus whoever is online. */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestScoreHolders(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        if (remaining.isEmpty() || "*".startsWith(remaining)) builder.suggest("*");
        net.minecraft.server.MinecraftServer server = ctx.getSource().getServer();
        if (server == null) return builder.buildFuture();
        for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getScoreboardName().toLowerCase().startsWith(remaining)) {
                builder.suggest(player.getScoreboardName());
            }
        }
        return builder.buildFuture();
    }



    /**
     * The presets, and "reset" to hand the timer back to the server default.
     *
     * <p>One list of one kind of thing. The two providers this replaces each
     * poured presets, values, timer names and "default" into the same
     * completion, because the argument underneath them had to accept all of
     * it.</p>
     */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPresets(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        if ("reset".startsWith(remaining)) builder.suggest("reset");
        for (TimerPositionPreset preset : TimerPositionPreset.values()) {
            String name = preset.name().toLowerCase();
            if (name.startsWith(remaining)) builder.suggest(name);
        }
        return builder.buildFuture();
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestDimensions(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        net.minecraft.server.MinecraftServer server = ctx.getSource().getServer();
        if (server != null) {
            String remaining = builder.getRemaining().toLowerCase();
            for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
                String dimId = VanillaCompat.dimensionId(level);
                if (dimId.toLowerCase().startsWith(remaining)) builder.suggest(dimId);
            }
        }
        return builder.buildFuture();
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("timer").requires(source ->
                        PermissionHelper.hasPermission(source, "ontime.command", 4))
                .then(Commands.literal("create")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_CREATE, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("hours", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 59))
                                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 59))
                                                        .executes(ctx -> LifecycleCommands.createTimer(ctx, false))
                                                        // The direction has to be spelled before a command can
                                                        // follow. Letting the command come straight after the
                                                        // seconds meant Brigadier tried the boolean first and
                                                        // swallowed the first word of any command beginning
                                                        // with "true" or "false".
                                                        .then(Commands.argument("countUp", BoolArgumentType.bool())
                                                                .executes(ctx -> LifecycleCommands.createTimer(ctx, BoolArgumentType.getBool(ctx, "countUp")))
                                                                .then(Commands.argument("command", StringArgumentType.greedyString())
                                                                        .executes(ctx -> LifecycleCommands.createTimerWithCommand(ctx,
                                                                                BoolArgumentType.getBool(ctx, "countUp"),
                                                                                StringArgumentType.getString(ctx, "command")))
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("set")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_SET, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .then(Commands.argument("hours", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 59))
                                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 59))
                                                        .executes(LifecycleCommands::setTimer)
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("start")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_START, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                // No selector: a global run, seen by whoever
                                // connects later. Exactly the 4.0.0 shape.
                                .executes(ctx -> RunCommands.start(ctx, null, com.mateof24.api.RunMode.SHARED))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> RunCommands.start(ctx,
                                                EntityArgument.getPlayers(ctx, "targets"),
                                                com.mateof24.api.RunMode.SHARED))
                                        .then(Commands.literal("shared")
                                                .executes(ctx -> RunCommands.start(ctx,
                                                        EntityArgument.getPlayers(ctx, "targets"),
                                                        com.mateof24.api.RunMode.SHARED)))
                                        .then(Commands.literal("each")
                                                .executes(ctx -> RunCommands.start(ctx,
                                                        EntityArgument.getPlayers(ctx, "targets"),
                                                        com.mateof24.api.RunMode.EACH)))
                                )
                        )
                )
                .then(selection("pause", PermissionNodes.TIMER_PAUSE,
                        ctx -> RunCommands.setRunning(ctx, Boolean.FALSE, null, null),
                        (ctx, name, targets) -> RunCommands.setRunning(ctx, Boolean.FALSE, name, targets)))
                .then(selection("resume", PermissionNodes.TIMER_PAUSE,
                        ctx -> RunCommands.setRunning(ctx, Boolean.TRUE, null, null),
                        (ctx, name, targets) -> RunCommands.setRunning(ctx, Boolean.TRUE, name, targets)))
                // No permission of its own: it only replays something the
                // caller already had the permission to stage, and the closure
                // belongs to them alone.
                .then(Commands.literal("confirm")
                        .executes(RunCommands::confirm)
                )
                .then(Commands.literal("audience")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_AUDIENCE, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(RunCommands::audienceList)
                                .then(Commands.literal("list")
                                        .executes(RunCommands::audienceList))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> RunCommands.audienceEdit(ctx, true,
                                                        EntityArgument.getPlayers(ctx, "targets")))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> RunCommands.audienceEdit(ctx, false,
                                                        EntityArgument.getPlayers(ctx, "targets")))))
                        )
                )
                .then(Commands.literal("remove")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_REMOVE, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(LifecycleCommands::removeTimer)
                        )
                )
                .then(Commands.literal("add")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_ADD, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .then(Commands.argument("hours", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 59))
                                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 59))
                                                        .executes(LifecycleCommands::addTime)
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("expr")
                        .then(Commands.literal("create")
                                .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_CREATE, 4))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("expression", StringArgumentType.greedyString())
                                                .executes(ctx -> LifecycleCommands.createTimerWithExpr(ctx, false))
                                        )
                                )
                        )
                        .then(Commands.literal("set")
                                .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_SET, 4))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(TIMER_SUGGESTIONS)
                                        .then(Commands.argument("expression", StringArgumentType.greedyString())
                                                .executes(LifecycleCommands::setTimerExpr)
                                        )
                                )
                        )
                        .then(Commands.literal("add")
                                .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_ADD, 4))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(TIMER_SUGGESTIONS)
                                        .then(Commands.argument("expression", StringArgumentType.greedyString())
                                                .executes(LifecycleCommands::addTimerExpr)
                                        )
                                )
                        )
                )
                .then(Commands.literal("list")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_LIST, 4))
                        .executes(InfoCommands::listTimers)
                )
                // Called 'gui' rather than 'panel' so it cannot be confused
                // with /timer webpanel, which is the other surface entirely.
                .then(Commands.literal("gui")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_GUI, 4))
                        .executes(InfoCommands::openPanel)
                )
                .then(Commands.literal("status")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_STATUS, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(InfoCommands::status)
                        )
                )
                // Both are player preferences, so they take players and never a
                // timer. Say who and say what: the bare form toggled whoever
                // typed it and the targets-only form toggled them, so the same
                // line could mute or unmute depending on the state it found.
                // A command block cannot reason about that.
                .then(Commands.literal("silent")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_SILENT, 4))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.literal("mute")
                                        .executes(ctx -> DisplayCommands.applySilentTargets(ctx, Boolean.TRUE)))
                                .then(Commands.literal("unmute")
                                        .executes(ctx -> DisplayCommands.applySilentTargets(ctx, Boolean.FALSE)))
                                .then(Commands.literal("toggle")
                                        .executes(ctx -> DisplayCommands.applySilentTargets(ctx, null)))
                        )
                )
                .then(Commands.literal("hide")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_HIDE, 4))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.literal("show")
                                        .executes(ctx -> DisplayCommands.applyHideTargets(ctx, Boolean.TRUE)))
                                .then(Commands.literal("hide")
                                        .executes(ctx -> DisplayCommands.applyHideTargets(ctx, Boolean.FALSE)))
                                .then(Commands.literal("toggle")
                                        .executes(ctx -> DisplayCommands.applyHideTargets(ctx, null)))
                        )
                )
                .then(selection("stop", PermissionNodes.TIMER_STOP,
                        ctx -> RunCommands.stop(ctx, null, null), RunCommands::stop))
                .then(selection("reset", PermissionNodes.TIMER_RESET,
                        ctx -> RunCommands.reset(ctx, null, null), RunCommands::reset))
                .then(Commands.literal("help")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_HELP, 4))
                        .executes(ctx -> HelpSystem.showHelpPage(ctx.getSource(), 1))
                        // Two arguments of different types rather than one word
                        // the handler re-parses in a try/catch. Brigadier picks
                        // the integer when the token is one, and a page number
                        // out of range is now refused instead of silently
                        // looked up as the name of a subcommand.
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> HelpSystem.showHelpPage(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "page")))
                        )
                        .then(Commands.argument("subcommand", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    String remaining = builder.getRemaining().toLowerCase();
                                    for (String topic : HelpSystem.topics()) {
                                        if (topic.startsWith(remaining)) builder.suggest(topic);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> HelpSystem.showCommandHelp(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "subcommand")))
                        )
                )
                // position and scale read the timer first and nothing else.
                // They used to accept a preset or a value in that same slot to
                // mean "the global default", which is why one argument had to
                // serve two shapes and why the suggestions offered names and
                // values mixed together. Server defaults are set from the
                // panel, not by overloading a per-timer command.
                .then(Commands.literal("position")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_POSITION, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(DisplayCommands::positionView)
                                .then(Commands.argument("preset", StringArgumentType.word())
                                        .suggests(TimerCommands::suggestPresets)
                                        .executes(DisplayCommands::positionSet)
                                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                                .then(Commands.argument("y", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> DisplayCommands.positionCustom(ctx,
                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                IntegerArgumentType.getInteger(ctx, "y")))
                                                )
                                        )
                                )
                        )
                )
                // Sound belonged to no timer at all: it wrote the server
                // default and there was no way to give one timer its own.
                .then(Commands.literal("sound")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_SOUND, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(DisplayCommands::soundView)
                                .then(Commands.literal("reset")
                                        .executes(DisplayCommands::soundReset)
                                )
                                .then(Commands.argument("soundId", VanillaCompat.idArgument())
                                        .suggests((context, builder) ->
                                                VanillaCompat.suggestSoundEvents(builder))
                                        .executes(ctx -> DisplayCommands.setSound(ctx,
                                                VanillaCompat.getIdArgument(ctx, "soundId"), 0.75f, 2.0f))
                                        .then(Commands.argument("volume", FloatArgumentType.floatArg(0.0f, 1.0f))
                                                .executes(ctx -> DisplayCommands.setSound(ctx,
                                                        VanillaCompat.getIdArgument(ctx, "soundId"),
                                                        FloatArgumentType.getFloat(ctx, "volume"), 2.0f))
                                                .then(Commands.argument("pitch", FloatArgumentType.floatArg(0.5f, 2.0f))
                                                        .executes(ctx -> DisplayCommands.setSound(ctx,
                                                                VanillaCompat.getIdArgument(ctx, "soundId"),
                                                                FloatArgumentType.getFloat(ctx, "volume"),
                                                                FloatArgumentType.getFloat(ctx, "pitch")))
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("scale")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_SCALE, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(DisplayCommands::scaleView)
                                // A float argument, not a word the handler
                                // reparses: the range is enforced by the parser
                                // and a bad value is refused before it runs.
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0.1f, 5.0f))
                                        .executes(DisplayCommands::scaleSet)
                                )
                                .then(Commands.literal("reset")
                                        .executes(DisplayCommands::scaleReset)
                                )
                        )
                )
                // /timer command is gone. It held one command, "the" finish
                // command, alongside the list that can hold several -- the same
                // idea kept in two places since 4.0.0 grew the list. A finish
                // command is now an entry of the list like any other.
                .then(Commands.literal("commands")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_COMMAND, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(BehaviorCommands::listScheduledCommands)
                                .then(Commands.literal("add")
                                        .then(Commands.literal("finish")
                                                .then(Commands.argument("command", StringArgumentType.greedyString())
                                                        .executes(ctx -> BehaviorCommands.addFinishCommand(ctx,
                                                                StringArgumentType.getString(ctx, "command")))
                                                )
                                        )
                                        .then(Commands.argument("hours", IntegerArgumentType.integer(0))
                                                .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 59))
                                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 59))
                                                                .then(Commands.argument("command", StringArgumentType.greedyString())
                                                                        .executes(ctx -> BehaviorCommands.addScheduledCommand(ctx,
                                                                                IntegerArgumentType.getInteger(ctx, "hours"),
                                                                                IntegerArgumentType.getInteger(ctx, "minutes"),
                                                                                IntegerArgumentType.getInteger(ctx, "seconds"),
                                                                                StringArgumentType.getString(ctx, "command")))
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("list")
                                        .executes(BehaviorCommands::listScheduledCommands)
                                )
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                .executes(ctx -> BehaviorCommands.removeScheduledCommand(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "index")))
                                        )
                                )
                                .then(Commands.literal("clear")
                                        .executes(BehaviorCommands::clearScheduledCommands)
                                )
                                // The pause between two of this timer's own
                                // commands. A datapack that builds a timer has
                                // to be able to set it; the server default is
                                // a default, not the only figure there is.
                                .then(Commands.literal("delay")
                                        .executes(BehaviorCommands::viewCommandDelay)
                                        .then(Commands.argument("ticks", IntegerArgumentType.integer(0, 72000))
                                                .executes(ctx -> BehaviorCommands.setCommandDelay(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "ticks")))
                                        )
                                        .then(Commands.literal("reset")
                                                .executes(BehaviorCommands::resetCommandDelay)
                                        )
                                )
                        )
                )
                .then(Commands.literal("title")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_TITLE, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(BehaviorCommands::viewTitles)
                                .then(Commands.literal("clear")
                                        .executes(BehaviorCommands::clearAllTitles)
                                )
                                .then(titlePosition("above"))
                                .then(titlePosition("below"))
                                .then(titlePosition("left"))
                                .then(titlePosition("right"))
                        )
                )
                .then(Commands.literal("repeat")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_REPEAT, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(BehaviorCommands::toggleRepeatInfinite)
                                .then(Commands.argument("count", IntegerArgumentType.integer(-1))
                                        .executes(ctx -> BehaviorCommands.setRepeatCount(ctx,
                                                IntegerArgumentType.getInteger(ctx, "count"), 0))
                                        .then(Commands.argument("cooldownSeconds", IntegerArgumentType.integer(0))
                                                .executes(ctx -> BehaviorCommands.setRepeatCount(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "count"),
                                                        IntegerArgumentType.getInteger(ctx, "cooldownSeconds")))
                                        )
                                )
                        )
                )
                .then(Commands.literal("sequence")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_SEQUENCE, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(BehaviorCommands::viewSequence)
                                .then(Commands.literal("clear")
                                        .executes(BehaviorCommands::clearSequence)
                                )
                                .then(Commands.argument("nextName", StringArgumentType.word())
                                        .suggests(TIMER_SUGGESTIONS)
                                        .executes(ctx -> BehaviorCommands.setSequence(ctx,
                                                StringArgumentType.getString(ctx, "nextName"), 0))
                                        .then(Commands.argument("cooldownSeconds", IntegerArgumentType.integer(0))
                                                .executes(ctx -> BehaviorCommands.setSequence(ctx,
                                                        StringArgumentType.getString(ctx, "nextName"),
                                                        IntegerArgumentType.getInteger(ctx, "cooldownSeconds")))
                                        )
                                )
                        )
                )
                .then(Commands.literal("export")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_EXPORT, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(ctx -> SharingCommands.exportTimer(ctx, StringArgumentType.getString(ctx, "name")))
                        )
                )
                .then(Commands.literal("import")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_IMPORT, 4))
                        .then(Commands.argument("filename", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    TimerStorage.getExportNames().stream()
                                            .filter(n -> n.toLowerCase().startsWith(builder.getRemaining().toLowerCase()))
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> SharingCommands.importTimer(ctx, StringArgumentType.getString(ctx, "filename"), null))
                                .then(Commands.argument("newname", StringArgumentType.word())
                                        .executes(ctx -> SharingCommands.importTimer(ctx,
                                                StringArgumentType.getString(ctx, "filename"),
                                                StringArgumentType.getString(ctx, "newname")))
                                )
                        )
                )
                .then(Commands.literal("clone")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_CLONE, 4))
                        .then(Commands.argument("source", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .then(Commands.argument("dest", StringArgumentType.word())
                                        .executes(ctx -> SharingCommands.cloneTimer(ctx,
                                                StringArgumentType.getString(ctx, "source"),
                                                StringArgumentType.getString(ctx, "dest")))
                                )
                        )
                )
                .then(Commands.literal("webpanel")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_WEBPANEL, 4))
                        .then(Commands.literal("start")
                                .executes(ctx -> WebPanelCommands.webPanelStart(ctx, ModConfig.getInstance().getWebPanelPort()))
                                .then(Commands.argument("port", IntegerArgumentType.integer(1024, 65535))
                                        .executes(ctx -> WebPanelCommands.webPanelStart(ctx, IntegerArgumentType.getInteger(ctx, "port")))
                                )
                        )
                        .then(Commands.literal("stop")
                                .executes(WebPanelCommands::webPanelStop)
                        )
                        .then(Commands.literal("info")
                                .executes(WebPanelCommands::webPanelInfo)
                        )
                )
                // One subtree for every reason a timer starts or ends.
                // /timer condition used to hold the scoreboard and expression
                // halves under a different grammar, where the action was a
                // literal in the node name (if / if_start) and the scoreboard
                // branch offered no action at all.
                .then(Commands.literal("trigger")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_TRIGGER, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(BehaviorCommands::listTriggers)
                                .then(Commands.literal("list")
                                        .executes(BehaviorCommands::listTriggers))
                                .then(Commands.literal("clear")
                                        .executes(BehaviorCommands::clearTriggers))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                .executes(ctx -> BehaviorCommands.removeTrigger(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "index")))))
                                .then(Commands.literal("add")
                                        .then(bareTrigger("player_join", Trigger.Kind.PLAYER_JOIN))
                                        .then(bareTrigger("player_leave", Trigger.Kind.PLAYER_LEAVE))
                                        .then(bareTrigger("player_death", Trigger.Kind.PLAYER_DEATH))
                                        .then(bareTrigger("player_respawn", Trigger.Kind.PLAYER_RESPAWN))
                                        .then(idTrigger("dimension_change", Trigger.Kind.DIMENSION_CHANGE,
                                                TimerCommands::suggestDimensions))
                                        .then(idTrigger("advancement", Trigger.Kind.ADVANCEMENT,
                                                TimerCommands::suggestAdvancements))
                                        .then(wordTrigger("ftb_quest", Trigger.Kind.FTB_QUEST))
                                        .then(wordTrigger("ftb_reward", Trigger.Kind.FTB_REWARD))
                                        .then(Commands.literal("scoreboard")
                                                .then(Commands.argument("objective", StringArgumentType.word())
                                                        .suggests(TimerCommands::suggestObjectives)
                                                        .then(Commands.argument("score", IntegerArgumentType.integer())
                                                                .then(scoreboardAction("start", Trigger.Action.START))
                                                                .then(scoreboardAction("finish", Trigger.Action.FINISH))
                                                        )
                                                )
                                        )
                                        .then(Commands.literal("expression")
                                                .then(expressionAction("start", Trigger.Action.START))
                                                .then(expressionAction("finish", Trigger.Action.FINISH))
                                        )
                                )
                        )
                )
        );
    }
}
