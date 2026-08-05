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
            for (Timer timer : TimerManager.getInstance().timersView()) {
                if (timer.getName().toLowerCase().startsWith(remaining)) {
                    builder.suggest(timer.getName());
                }
            }

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
                .executes(bare)
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

    /** First argument of /timer position: a preset (legacy form), "default", or a timer. */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPresetsAndTimers(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        if ("default".startsWith(remaining)) builder.suggest("default");
        for (TimerPositionPreset preset : TimerPositionPreset.values()) {
            String name = preset.name().toLowerCase();
            if (name.startsWith(remaining)) builder.suggest(name);
        }
        for (Timer timer : TimerManager.getInstance().timersView()) {
            if (timer.getName().toLowerCase().startsWith(remaining)) builder.suggest(timer.getName());
        }
        return builder.buildFuture();
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPresetsAndClear(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        if ("clear".startsWith(remaining)) builder.suggest("clear");
        for (TimerPositionPreset preset : TimerPositionPreset.values()) {
            String name = preset.name().toLowerCase();
            if (name.startsWith(remaining)) builder.suggest(name);
        }
        return builder.buildFuture();
    }

    /** First argument of /timer scale: a number (legacy form), "default", or a timer. */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestScaleAndTimers(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        if ("default".startsWith(remaining)) builder.suggest("default");
        for (String value : new String[]{"0.5", "1.0", "1.5", "2.0"}) {
            if (value.startsWith(remaining)) builder.suggest(value);
        }
        for (Timer timer : TimerManager.getInstance().timersView()) {
            if (timer.getName().toLowerCase().startsWith(remaining)) builder.suggest(timer.getName());
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
                        // Bare: the 4.0.0 toggle, so existing command blocks
                        // behave the same. Any argument is explicit and pauses.
                        ctx -> RunCommands.setRunning(ctx, null, null, null),
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
                .then(Commands.literal("status")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_STATUS, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(InfoCommands::status)
                        )
                )
                .then(Commands.literal("silent")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_SILENT, 4))
                        .executes(DisplayCommands::toggleSilentSelf)
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> DisplayCommands.applySilentTargets(ctx, null))
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
                        .executes(DisplayCommands::toggleHideSelf)
                        .then(Commands.argument("targets", net.minecraft.commands.arguments.EntityArgument.players())
                                .executes(ctx -> DisplayCommands.applyHideTargets(ctx, null))
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
                        .then(Commands.argument("pageOrCommand", StringArgumentType.word())
                                .executes(ctx -> {
                                    String arg = StringArgumentType.getString(ctx, "pageOrCommand");
                                    try {
                                        int page = Integer.parseInt(arg);
                                        return HelpSystem.showHelpPage(ctx.getSource(), page);
                                    } catch (NumberFormatException e) {
                                        return HelpSystem.showCommandHelp(ctx.getSource(), arg);
                                    }
                                })
                        )
                )
                // position and scale both take "<default|timer> <value>" plus
                // the 4.0.0 one-argument form that means the global default.
                // The tree cannot separate them — a preset name and a timer
                // name are both words — so both shapes share one argument and
                // the handler decides. That is what keeps every existing
                // /timer position bossbar parsing.
                .then(Commands.literal("position")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_POSITION, 4))
                        .then(Commands.argument("first", StringArgumentType.word())
                                .suggests(TimerCommands::suggestPresetsAndTimers)
                                .executes(DisplayCommands::position)
                                .then(Commands.argument("second", StringArgumentType.word())
                                        .suggests(TimerCommands::suggestPresetsAndClear)
                                        .executes(DisplayCommands::position2)
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
                .then(Commands.literal("sound")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_SOUND, 4))
                        .then(Commands.argument("soundId", VanillaCompat.idArgument())
                                .suggests((context, builder) ->
                                        VanillaCompat.suggestSoundEvents(builder))
                                .executes(ctx -> DisplayCommands.setSoundDefault(ctx,
                                        VanillaCompat.getIdArgument(ctx, "soundId")))
                                .then(Commands.argument("volume", FloatArgumentType.floatArg(0.0f, 1.0f))
                                        .executes(ctx -> DisplayCommands.setSoundWithVolume(ctx,
                                                VanillaCompat.getIdArgument(ctx, "soundId"),
                                                FloatArgumentType.getFloat(ctx, "volume")))
                                        .then(Commands.argument("pitch", FloatArgumentType.floatArg(0.5f, 2.0f))
                                                .executes(ctx -> DisplayCommands.setSoundFull(ctx,
                                                        VanillaCompat.getIdArgument(ctx, "soundId"),
                                                        FloatArgumentType.getFloat(ctx, "volume"),
                                                        FloatArgumentType.getFloat(ctx, "pitch")))
                                        )
                                )
                        )
                )
                .then(Commands.literal("scale")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_SCALE, 4))
                        .then(Commands.argument("first", StringArgumentType.word())
                                .suggests(TimerCommands::suggestScaleAndTimers)
                                .executes(DisplayCommands::scale)
                                .then(Commands.argument("second", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (String option : new String[]{"clear", "0.5", "1.0", "1.5", "2.0"}) {
                                                if (option.startsWith(builder.getRemaining())) builder.suggest(option);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(DisplayCommands::scale2)
                                )
                        )
                )
                .then(Commands.literal("command")
                        .requires(source -> PermissionHelper.hasPermission(source, PermissionNodes.TIMER_COMMAND, 4))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TIMER_SUGGESTIONS)
                                .executes(BehaviorCommands::viewTimerCommand)
                                .then(Commands.argument("command", StringArgumentType.greedyString())
                                        .executes(ctx -> BehaviorCommands.updateTimerCommand(ctx,
                                                StringArgumentType.getString(ctx, "command")))
                                )
                        )
                )
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
