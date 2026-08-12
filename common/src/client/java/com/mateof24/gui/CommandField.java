package com.mateof24.gui;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * A command box that behaves like the one on a command block.
 *
 * <p>It is the same class vanilla uses, wired the same way: the completions
 * come from the dispatcher the server sent this client, so they are the
 * server's actual commands — a datapack's functions and another mod's commands
 * included — rather than a list this mod would have to keep in step.</p>
 *
 * <p>Two things vanilla's command block does not do are done here. The text is
 * coloured argument by argument, the way the chat line colours it, and a
 * command that does not parse goes red from the point it stops making sense.
 * The colouring is not copied out of {@code CommandSuggestions} — its
 * formatter is not public — but taken from the same parse, so the ranges are
 * the ones brigadier itself decided.</p>
 *
 * <p>Everything about this class is identical across every version the mod
 * ships for; the three screens differ only in the shape of the render and
 * input calls, and each of them makes those against {@link #suggestions()}.</p>
 */
public final class CommandField {

    /**
     * The colours an argument takes, in turn.
     *
     * <p>The same five, in the same order, that chat cycles through, so a
     * command reads here exactly as it reads when it is typed.</p>
     */
    private static final ChatFormatting[] ARGUMENT_COLOURS = {
            ChatFormatting.AQUA, ChatFormatting.YELLOW, ChatFormatting.GREEN,
            ChatFormatting.LIGHT_PURPLE, ChatFormatting.GOLD};

    private static final Style PLAIN = Style.EMPTY;
    private static final Style BROKEN = Style.EMPTY.withColor(ChatFormatting.RED);

    private CommandSuggestions suggestions;
    private EditBox bound;

    /**
     * Attaches to a box, building the completion list it will use.
     *
     * <p>Rebuilt only when the box itself changes: the panel lays itself out
     * again on every click, and a fresh completion list on each of those would
     * close the popup the moment anybody moved the mouse.</p>
     */
    public void bind(Screen screen, EditBox box) {
        if (box == null) {
            suggestions = null;
            bound = null;
            showing = false;
            return;
        }
        if (box == bound && suggestions != null) return;

        Minecraft minecraft = Minecraft.getInstance();
        // The command block's own arguments, unchanged. Its field sits near
        // the top of the screen and the popup opens below it, which is where
        // this field sits too now — it moved above the list it adds to, so
        // the list it offers no longer covers what you are adding to.
        suggestions = new CommandSuggestions(minecraft, screen, box, minecraft.font,
                true, true, 0, 7, false, 0xD0000000);
        suggestions.setAllowSuggestions(false);
        bound = box;
    }

    /** Recomputes the completions. Called from the box's own responder. */
    public void refresh() {
        if (suggestions != null && bound != null && bound.isFocused()) {
            suggestions.updateCommandInfo();
        }
    }

    /**
     * Follows the focus, once a frame.
     *
     * <p>A list offered to a box nobody has clicked in is a list in the way:
     * opening the page put every command on screen before anybody had asked
     * for one. It appears when the box is focused and goes when it is not.</p>
     */
    public void tick() {
        if (suggestions == null || bound == null) return;
        boolean focused = bound.isFocused();
        if (focused == showing) return;
        showing = focused;
        suggestions.setAllowSuggestions(focused);
        if (focused) {
            suggestions.updateCommandInfo();
        } else {
            suggestions.hide();
        }
    }

    private boolean showing;

    /**
     * The vanilla control, for the screen to render and feed input to.
     *
     * <p>Handed over rather than wrapped because its render and input methods
     * are the three that change shape between versions, and the screens are
     * where that difference already lives.</p>
     */
    public CommandSuggestions suggestions() {
        return suggestions;
    }

    public boolean isVisible() {
        return suggestions != null && suggestions.isVisible();
    }

    /** True while the box it belongs to has the caret. */
    public boolean isFocused() {
        return bound != null && bound.isFocused();
    }

    // The dispatcher's own type argument is the client suggestion provider on
    // some versions and the shared one on others, and this file is shared by
    // all of them. Nothing here needs to know which: it parses a string with
    // the source that came from the same connection, and brigadier's raw type
    // takes both without a cast at every call.

    /**
     * Whether the server would accept this as a command.
     *
     * <p>Asked of the dispatcher, not of a pattern: "sayy hello" is refused
     * because no command is called that, and "give @s" because give wants more
     * than that. An empty box is not wrong, only unfinished.</p>
     */
    public static boolean parses(String command) {
        String text = command.trim();
        if (text.isEmpty()) return false;
        ParseResults<?> parse = parse(text);
        if (parse == null) return true; // no dispatcher: nothing to judge it by
        return parse.getExceptions().isEmpty()
                && parse.getReader().getRemainingLength() == 0
                && parse.getContext().getRange().getEnd() > 0;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ParseResults<?> parse(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return null;
        CommandDispatcher commands = minecraft.player.connection.getCommands();
        if (commands == null) return null;
        return commands.parse(text, minecraft.player.connection.getSuggestionsProvider());
    }

    /**
     * The box's own text, coloured by what brigadier made of it.
     *
     * <p>Installed by the screen rather than here: the box took a
     * {@code setFormatter} before 1.21.10 and an {@code addFormatter} from
     * there on, which is the one split the three screens already are.</p>
     *
     * @param offset where in the whole string this fragment starts, which the
     *               box passes because it draws only what is scrolled into view
     */
    public static FormattedCharSequence colour(String text, int offset) {
        ParseResults<?> parse = parse(text);
        if (parse == null) return FormattedCharSequence.forward(text, PLAIN);

        List<FormattedCharSequence> parts = new ArrayList<>();
        int cursor = 0;
        int colour = 0;

        for (ParsedCommandNode<?> node : parse.getContext().getLastChild().getNodes()) {
            StringRange range = node.getRange();
            if (range.getStart() >= text.length()) break;
            int start = Math.max(cursor, range.getStart());
            int end = Math.min(text.length(), range.getEnd());
            if (end <= start) continue;

            if (start > cursor) {
                parts.add(cut(text, cursor, start, offset, PLAIN));
            }
            // Literals are the command's own words and stay plain; arguments
            // are what somebody filled in, and each one takes the next colour.
            Style style = node.getNode() instanceof ArgumentCommandNode
                    ? Style.EMPTY.withColor(ARGUMENT_COLOURS[colour++ % ARGUMENT_COLOURS.length])
                    : PLAIN;
            parts.add(cut(text, start, end, offset, style));
            cursor = end;
        }

        // Whatever brigadier could not make sense of, from where it gave up.
        if (cursor < text.length()) {
            boolean broken = !parse.getExceptions().isEmpty()
                    || parse.getReader().getRemainingLength() > 0;
            parts.add(cut(text, cursor, text.length(), offset, broken ? BROKEN : PLAIN));
        }
        return FormattedCharSequence.composite(parts);
    }

    /** One run of the string, minus whatever the box has scrolled past. */
    private static FormattedCharSequence cut(String text, int from, int to, int offset, Style style) {
        int start = Math.max(from, offset);
        if (start >= to) return FormattedCharSequence.EMPTY;
        return FormattedCharSequence.forward(text.substring(start, to), style);
    }

    /** The name of the box, which is what a screen reader is given. */
    public Component narration() {
        return suggestions == null ? Component.empty() : suggestions.getNarrationMessage();
    }
}
