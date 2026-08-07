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
                                                        .then(Commands.argument("countUp", BoolArgumentType.bool())
                                                                .executes(ctx -> LifecycleCommands.createTimer(ctx, BoolArgumentType.getBool(ctx, "countUp")))
                                                                .then(Commands.argument("command", StringArgumentType.greedyString())
                                                                        .executes(ctx -> LifecycleCommands.createTimerWithCommand(ctx,
                                                                                BoolArgumentType.getBool(ctx, "countUp"),
                                                                                StringArgumentType.getString(ctx, "command")))
                                                                )
                                                        )
                                                        .then(Commands.argument("command", StringArgumentType.greedyString())
                                                                .executes(ctx -> LifecycleCommands.createTimerWithCommand(ctx, false,
                                                                        StringArgumentType.getString(ctx, "command")))
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
                .then(Commands.literal("condition")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_CONDITION, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(BehaviorCommands::viewCondition)
                                .then(Commands.literal("clear")
                                        .executes(BehaviorCommands::clearCondition)
                                )
                                .then(Commands.literal("if")
                                        .then(Commands.argument("expression", StringArgumentType.greedyString())
                                                .executes(ctx -> BehaviorCommands.setConditionExpression(ctx,
                                                        StringArgumentType.getString(ctx, "expression"), "finish"))
                                        )
                                )
                                .then(Commands.literal("if_start")
                                        .then(Commands.argument("expression", StringArgumentType.greedyString())
                                                .executes(ctx -> BehaviorCommands.setConditionExpression(ctx,
                                                        StringArgumentType.getString(ctx, "expression"), "start"))
                                        )
                                )
                                .then(Commands.argument("objective", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            ctx.getSource().getServer().getScoreboard().getObjectives()
                                                    .forEach(obj -> {
                                                        if (obj.getName().toLowerCase().startsWith(builder.getRemaining().toLowerCase()))
                                                            builder.suggest(obj.getName());
                                                    });
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("score", IntegerArgumentType.integer(0))
                                                .executes(ctx -> BehaviorCommands.setCondition(ctx,
                                                        StringArgumentType.getString(ctx, "objective"),
                                                        IntegerArgumentType.getInteger(ctx, "score"),
                                                        "*"))
                                                .then(Commands.argument("target", StringArgumentType.word())
                                                        .suggests((ctx, builder) -> {
                                                            String remaining = builder.getRemaining().toLowerCase();
                                                            if ("*".startsWith(remaining)) builder.suggest("*");
                                                            ctx.getSource().getServer().getPlayerList().getPlayers()
                                                                    .forEach(p -> {
                                                                        if (p.getScoreboardName().toLowerCase().startsWith(remaining))
                                                                            builder.suggest(p.getScoreboardName());
                                                                    });
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(ctx -> BehaviorCommands.setCondition(ctx,
                                                                StringArgumentType.getString(ctx, "objective"),
                                                                IntegerArgumentType.getInteger(ctx, "score"),
                                                                StringArgumentType.getString(ctx, "target")))
                                                )
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
                .then(Commands.literal("trigger")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_TRIGGER, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(BehaviorCommands::viewTrigger)
                                .then(Commands.literal("clear")
                                        .executes(BehaviorCommands::clearTrigger)
                                )
                                .then(Commands.literal("player_death")
                                        .executes(ctx -> BehaviorCommands.setTrigger(ctx, "player_death", "finish"))
                                        .then(Commands.argument("action", StringArgumentType.word())
                                                .suggests((c, b) -> { b.suggest("finish"); b.suggest("start"); return b.buildFuture(); })
                                                .executes(ctx -> BehaviorCommands.setTrigger(ctx, "player_death",
                                                        StringArgumentType.getString(ctx, "action")))
                                        )
                                )
                                .then(Commands.literal("dimension_change")
                                        .executes(ctx -> BehaviorCommands.setTrigger(ctx, "dimension_change", "finish"))
                                        .then(Commands.argument("dimension", VanillaCompat.idArgument())
                                                .suggests(TimerCommands::suggestDimensions)
                                                .executes(ctx -> BehaviorCommands.setTrigger(ctx, "dimension_change:" +
                                                        VanillaCompat.getIdArgument(ctx, "dimension"), "finish"))
                                                .then(Commands.argument("action", StringArgumentType.word())
                                                        .suggests((c, b) -> { b.suggest("finish"); b.suggest("start"); return b.buildFuture(); })
                                                        .executes(ctx -> BehaviorCommands.setTrigger(ctx, "dimension_change:" +
                                                                        VanillaCompat.getIdArgument(ctx, "dimension"),
                                                                StringArgumentType.getString(ctx, "action")))
                                                )
                                        )
                                )
                                .then(Commands.literal("advancement")
                                        .then(Commands.argument("advancement_id", VanillaCompat.idArgument())
                                                .executes(ctx -> BehaviorCommands.setTrigger(ctx, "advancement:" +
                                                        VanillaCompat.getIdArgument(ctx, "advancement_id"), "finish"))
                                                .then(Commands.argument("action", StringArgumentType.word())
                                                        .suggests((c, b) -> { b.suggest("finish"); b.suggest("start"); return b.buildFuture(); })
                                                        .executes(ctx -> BehaviorCommands.setTrigger(ctx, "advancement:" +
                                                                        VanillaCompat.getIdArgument(ctx, "advancement_id"),
                                                                StringArgumentType.getString(ctx, "action")))
                                                )
                                        )
                                )
                                .then(Commands.literal("ftb_quest")
                                        .then(Commands.argument("quest_id", StringArgumentType.word())
                                                .executes(ctx -> BehaviorCommands.setTrigger(ctx, "ftb_quest:quest:" +
                                                        StringArgumentType.getString(ctx, "quest_id"), "finish"))
                                                .then(Commands.argument("action", StringArgumentType.word())
                                                        .suggests((c, b) -> { b.suggest("finish"); b.suggest("start"); return b.buildFuture(); })
                                                        .executes(ctx -> BehaviorCommands.setTrigger(ctx, "ftb_quest:quest:" +
                                                                        StringArgumentType.getString(ctx, "quest_id"),
                                                                StringArgumentType.getString(ctx, "action")))
                                                )
                                        )
                                )
                                .then(Commands.literal("ftb_reward")
                                        .then(Commands.argument("reward_id", StringArgumentType.word())
                                                .executes(ctx -> BehaviorCommands.setTrigger(ctx, "ftb_quest:reward:" +
                                                        StringArgumentType.getString(ctx, "reward_id"), "finish"))
                                                .then(Commands.argument("action", StringArgumentType.word())
                                                        .suggests((c, b) -> { b.suggest("finish"); b.suggest("start"); return b.buildFuture(); })
                                                        .executes(ctx -> BehaviorCommands.setTrigger(ctx, "ftb_quest:reward:" +
                                                                        StringArgumentType.getString(ctx, "reward_id"),
                                                                StringArgumentType.getString(ctx, "action")))
                                                )
                                        )
                                )
                        )
                )
        );
    }
}
