package com.mateof24.gui;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Marks a text field when what is typed in it cannot be used, and completes the
 * ones that name something from a registry.
 *
 * <p>The two belong together: a field either takes an id, or a number in a
 * range, and that same answer decides both what counts as valid and what can be
 * offered. Registering a field once with the rule it follows keeps the two from
 * disagreeing.</p>
 *
 * <p>Invalid text turns red rather than being rejected as it is typed. Half of
 * an id is invalid on the way to a valid one, so refusing keystrokes would make
 * the field unusable; the colour says the value is not usable <em>yet</em>. An
 * empty field is never marked — nothing has been typed wrong.</p>
 *
 * <p>The completion list deliberately mirrors the one the chat box shows for
 * commands: same panel colour, same line height, the selected row in yellow,
 * arrow keys to move through it, and the remainder of the selection written
 * into the field as grey ghost text. The colours and metrics are the ones
 * {@code CommandSuggestions} itself uses, so the two read as the same control
 * rather than as a lookalike. Matching follows the same rule as a command
 * argument too: text with no namespace matches against the path, which is what
 * lets {@code bell} find {@code minecraft:block.note_block.bell}.</p>
 *
 * <p>Drawn rather than built out of widgets. Seven buttons stacked under the
 * field was the first attempt, and it could not do any of this: no ghost text,
 * no keyboard, no highlight, and every row the width of the field whatever the
 * id measured. It also had to hide whatever it covered, because a widget
 * underneath would take the click meant for the row on top. A drawn list has no
 * such problem — it is painted after the widgets and takes its clicks before
 * them.</p>
 */
public final class FieldAssist {

    /** Where a field's completions come from, if it has any. */
    public enum Source {
        NONE,
        /** Every sound event the client knows about. */
        SOUNDS,
        /** The timers that exist right now, which the panel refreshes each layout. */
        TIMERS,
        /**
         * Every advancement this client knows about.
         *
         * <p>Read from the connection rather than from a registry: advancements
         * are data, so a pack can add them and the server sends what it has.
         * That also means the list is empty on the title screen, which the
         * panel never is.</p>
         */
        ADVANCEMENTS,
        /** Every dimension that exists, sent in the same snapshot. */
        DIMENSIONS,
        /** Whoever is online, which is who a list of names can name. */
        PLAYERS,
        /**
         * The five selectors, and the keys one can be narrowed by.
         *
         * <p>Written out rather than read from the game: the parser that knows
         * them takes a live command source and a whole context, which a text
         * box on a screen has neither of. The list is small and it is fixed by
         * the game, not by what any pack ships.</p>
         */
        SELECTORS
    }

    /** Clearance the list keeps from the edge of the screen. */
    private static final int MARGIN = 12;

    // Taken from CommandSuggestions so the list is indistinguishable from the
    // one chat draws.
    private static final int MAX_SHOWN = 10;
    private static final int LINE_HEIGHT = 12;
    private static final int PANEL_COLOR = 0xD0000000;
    private static final int TEXT_COLOR = 0xFFAAAAAA;
    private static final int SELECTED_COLOR = 0xFFFFFF00;

    // Full alpha, deliberately. Vanilla's own EditBox default was 0xE0E0E0
    // until 1.21.5 and 0xFFE0E0E0 from 1.21.6 on — measured with javap across
    // the range — because the colour stopped being padded to opaque on the way
    // to the font. Passing the older constant on a newer version is alpha zero,
    // which draws a field that looks empty however much you type into it.
    private static final int INVALID_TEXT = 0xFFFF5555;
    private static final int VALID_TEXT = 0xFFE0E0E0;

    /** Keys handled here, named so the three screen files agree on them. */
    public static final int KEY_UP = 265;
    public static final int KEY_DOWN = 264;
    public static final int KEY_TAB = 258;
    public static final int KEY_ENTER = 257;
    public static final int KEY_KP_ENTER = 335;
    public static final int KEY_ESCAPE = 256;

    private record Field(EditBox box, Predicate<String> valid, Source source,
                        java.util.function.IntSupplier tint, Tooltip tooltip) {}

    private final List<Field> fields = new ArrayList<>();
    private final List<String> matches = new ArrayList<>();
    private EditBox target;
    private int selected;
    /** First row drawn, so a long list scrolls with the selection instead of being cut off. */
    private int offset;

    private PanelHost host;

    /** Where the pointer was last frame, so a still one stops voting. */
    private int lastMouseX = Integer.MIN_VALUE, lastMouseY = Integer.MIN_VALUE;

    /** Cached because a registry walk per frame is a walk per frame. */
    private static List<String> soundIds = null;

    /** Set by the panel: what exists is not something this class can know. */
    private List<String> timerNames = List.of();

    public void setTimerNames(List<String> names) {
        timerNames = names == null ? List.of() : names;
    }

    /** Set by the panel, out of the server's snapshot. */
    private List<String> advancementIds = List.of();

    public void setAdvancementIds(List<String> ids) {
        advancementIds = ids == null ? List.of() : ids;
    }

    private List<String> dimensionIds = List.of();
    private List<String> playerNames = List.of();

    public void setDimensionIds(List<String> ids) {
        dimensionIds = ids == null ? List.of() : ids;
    }

    public void setPlayerNames(List<String> names) {
        playerNames = names == null ? List.of() : names;
    }

    /** Forgets every field; call when the screen rebuilds its widgets. */
    public void clear() {
        fields.clear();
        close();
    }

    public void setHost(PanelHost host) {
        this.host = host;
    }

    /**
     * Registers a field.
     *
     * <p>The tooltip is handed over rather than left on the widget because it
     * has to come off again: vanilla shows a widget's tooltip while it is
     * <em>focused</em>, not only while it is hovered, so one left on a text
     * field hangs over the text being typed into it and will not go away.</p>
     *
     * @param tint the colour for valid text, or null for the ordinary one
     */
    public void add(EditBox box, Predicate<String> valid, Source source,
                    Tooltip tooltip, java.util.function.IntSupplier tint) {
        if (box != null) fields.add(new Field(box, valid, source, tint, tooltip));
    }

    public void add(EditBox box, Predicate<String> valid, Source source) {
        add(box, valid, source, null, null);
    }

    public void add(EditBox box, Predicate<String> valid) {
        add(box, valid, Source.NONE, null, null);
    }

    // ---- rules ----

    // The rules themselves live in InputRules, which knows nothing about
    // screens and can therefore be run against a list of cases. These are the
    // names the widgets here already call them by.

    /**
     * What to offer in a selector box, given what is in it.
     *
     * <p>The five selectors while it is empty or still on the {@code @}, and
     * the keys a selector takes once a bracket is open — the same two lists
     * chat offers, at the same two moments.</p>
     */
    private List<String> selectorSuggestions() {
        String typed = target == null ? "" : target.getValue();
        int bracket = typed.indexOf('[');
        if (bracket < 0) return SELECTOR_HEADS;

        // Inside the brackets: complete the key being typed, and keep what is
        // already there so accepting one does not throw the rest away.
        String prefix = typed.substring(0, Math.max(bracket + 1, typed.lastIndexOf(',') + 1));
        List<String> out = new ArrayList<>();
        for (String key : SELECTOR_KEYS) out.add(prefix + key);
        return out;
    }

    private static final List<String> SELECTOR_HEADS =
            List.of("@a", "@e", "@n", "@p", "@r", "@s");

    /** The arguments a selector takes, as vanilla spells them. */
    private static final List<String> SELECTOR_KEYS = List.of(
            "advancements=", "distance=", "dx=", "dy=", "dz=", "gamemode=",
            "level=", "limit=", "name=", "nbt=", "predicate=", "scores=",
            "sort=", "tag=", "team=", "type=", "x=", "x_rotation=", "y=",
            "y_rotation=", "z=");

    public static Predicate<String> id() { return com.mateof24.trigger.InputRules.id(); }

    public static Predicate<String> selector() {
        return com.mateof24.trigger.InputRules.selector();
    }

    public static Predicate<String> nameList() {
        return com.mateof24.trigger.InputRules.nameList();
    }

    public static Predicate<String> intBetween(long min, long max) {
        return text -> {
            try {
                long value = Long.parseLong(text.trim());
                return value >= min && value <= max;
            } catch (NumberFormatException e) {
                return false;
            }
        };
    }

    public static Predicate<String> decimalBetween(float min, float max) {
        return text -> {
            try {
                float value = Float.parseFloat(text.trim());
                return value >= min && value <= max;
            } catch (NumberFormatException e) {
                return false;
            }
        };
    }

    /** {@code #RRGGBB}, with or without the hash. */
    public static Predicate<String> hexColor() {
        return text -> SettingsForm.colorOf(text) != null;
    }

    // ---- per-frame ----

    /**
     * Recolours every field, recomputes the completions for whichever one has
     * focus, and takes the pointer's row as the highlight.
     *
     * <p>Call this <em>before</em> the widgets are drawn. Everything it decides
     * — the text colour and the ghost text — is read by {@link EditBox} as it
     * draws itself, so deciding afterwards leaves the field showing the
     * previous frame's answer: while typing, the ghost text is one character
     * too long for a frame and the whole line appears to jump right and snap
     * back.</p>
     */
    public void update(int mouseX, int mouseY) {
        EditBox focused = null;
        Source source = Source.NONE;
        for (Field field : fields) {
            EditBox box = field.box();
            String text = box.getValue();
            boolean bad = !text.isBlank() && !field.valid().test(text);
            // Only ask a tinted field for its colour once the text is known to
            // parse. Asking first is what crashed the game: half of #FF8800 is
            // not a colour, and the supplier had nothing to return.
            box.setTextColor(bad || field.tint() == null
                    ? (bad ? INVALID_TEXT : VALID_TEXT)
                    : field.tint().getAsInt());

            // Off while this field is being typed into, back on when it is not.
            if (field.tooltip() != null) {
                box.setTooltip(box.isFocused() ? null : field.tooltip());
            }

            if (focused == null && box.isFocused() && field.source() != Source.NONE) {
                focused = box;
                source = field.source();
            }
        }
        if (focused != target) {
            close();
            target = focused;
        }
        if (target == null) return;

        String previous = selectedText();
        collect(source, target.getValue());
        // Keep the highlight on the same entry while more of its name is typed,
        // rather than snapping back to the top on every keystroke.
        selected = Math.max(0, matches.indexOf(previous));
        clampOffset();
        hover(mouseX, mouseY);
        updateGhost(target.getValue());
    }

    /**
     * Moves the highlight to the row the pointer is over — but only when the
     * pointer has actually moved.
     *
     * <p>This runs every frame, and the list opens directly under the field
     * that was just clicked, so the pointer is usually resting on it. Voting
     * unconditionally meant an arrow key moved the highlight and the very next
     * frame put it back under the cursor: the keyboard looked dead and only the
     * mouse appeared to work at all.</p>
     */
    private void hover(int mouseX, int mouseY) {
        boolean moved = mouseX != lastMouseX || mouseY != lastMouseY;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (!moved || matches.isEmpty()) return;

        int row = (mouseY - popupTop()) / LINE_HEIGHT;
        int w = width();
        int x = popupX(w);
        if (row >= 0 && row < shownCount() && mouseX >= x && mouseX <= x + w) {
            selected = offset + row;
            clampOffset();
        }
    }

    private void collect(Source source, String text) {
        matches.clear();
        String typed = text.trim().toLowerCase(Locale.ROOT);

        // An empty box offers everything, in order, which is what the chat
        // line does the moment a slash is typed. Waiting for a first letter
        // meant you had to already know what you were looking for.
        if (typed.isEmpty()) {
            List<String> all = new ArrayList<>(candidates(source));
            all.sort(String.CASE_INSENSITIVE_ORDER);
            for (String candidate : all) {
                matches.add(candidate);
                if (matches.size() >= MAX_SHOWN * 4) break;
            }
            return;
        }

        for (String candidate : candidates(source)) {
            // Matched in lower case, offered as written: a sound id is already
            // lower case, but a timer is called whatever somebody called it,
            // and typing 'tur' should still find 'Turno'.
            String folded = candidate.toLowerCase(Locale.ROOT);
            if (folded.equals(typed) || folded.equals("minecraft:" + typed)) {
                matches.clear();
                return; // already an exact match; nothing useful left to offer
            }
            if (startsWithLoosely(folded, typed)) {
                matches.add(candidate);
                if (matches.size() >= MAX_SHOWN * 4) {
                    break; // enough to scroll through; the rest would never be reached
                }
            }
        }
        // Alphabetical, the way every list vanilla offers is.
        matches.sort(String.CASE_INSENSITIVE_ORDER);
    }

    /**
     * Whether a candidate answers to what has been typed, by vanilla's rule.
     *
     * <p>Read out of {@code SharedSuggestionProvider} rather than guessed at:
     * the needle has to start a <em>segment</em>, where a segment begins at the
     * front of the string or straight after one of {@code . _ /}. That is why
     * typing {@code exp} into {@code /playsound} finds
     * {@code entity.experience_orb.pickup} and {@code entity.generic.explode}
     * alike, and it is the difference between a completion list and a filter.
     * With no namespace typed, the namespace and the path are each tried, the
     * path only for {@code minecraft} — the same two chances vanilla gives.</p>
     */
    private static boolean startsWithLoosely(String candidate, String needle) {
        if (needle.indexOf(':') >= 0) return matchesSegment(candidate, needle);

        int colon = candidate.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : candidate.substring(0, colon);
        String path = colon < 0 ? candidate : candidate.substring(colon + 1);
        return matchesSegment(namespace, needle)
                || ("minecraft".equals(namespace) && matchesSegment(path, needle));
    }

    /** True when {@code needle} starts the text or any segment of it. */
    private static boolean matchesSegment(String text, String needle) {
        for (int i = 0; !text.startsWith(needle, i); i++) {
            i = indexOfSplitter(text, i);
            if (i < 0) return false;
        }
        return true;
    }

    /** The separators vanilla treats as the start of a new segment. */
    private static int indexOfSplitter(String text, int from) {
        for (int i = Math.max(0, from); i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.' || c == '_' || c == '/') return i;
        }
        return -1;
    }

    private List<String> candidates(Source source) {
        if (source == Source.TIMERS) return timerNames;
        if (source == Source.ADVANCEMENTS) return advancementIds;
        if (source == Source.DIMENSIONS) return dimensionIds;
        if (source == Source.PLAYERS) return playerNames;
        if (source == Source.SELECTORS) return selectorSuggestions();
        if (source != Source.SOUNDS) return List.of();
        if (soundIds == null) {
            List<String> ids = new ArrayList<>();
            try {
                // Typed as Object on purpose: the id class is ResourceLocation
                // before 1.21.11 and Identifier after, and all that is wanted
                // here is its text.
                for (Object id : net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.keySet()) {
                    ids.add(id.toString());
                }
            } catch (Throwable ignored) {
                // No registry on this side; the field still takes typing.
            }
            java.util.Collections.sort(ids);
            soundIds = ids;
        }
        return soundIds;
    }


    /**
     * Writes the rest of the highlighted entry into the field as ghost text.
     *
     * <p>Trimmed to what is left of the field. Vanilla draws the suggestion
     * straight after the visible text and clips nothing, so an untrimmed one
     * runs out of the box and across whatever is beside it — and a sound id is
     * routinely longer than the field it goes in.</p>
     */
    private void updateGhost(String typed) {
        if (target == null) return;
        String selection = selectedText();
        if (selection == null || selection.length() <= typed.length()
                || !selection.regionMatches(true, 0, typed, 0, typed.length())) {
            target.setSuggestion(null);
            return;
        }

        String rest = selection.substring(typed.length());
        net.minecraft.client.gui.Font font = host != null ? host.font() : null;
        if (font != null) {
            int room = target.getInnerWidth() - font.width(typed);
            rest = room <= 0 ? "" : font.plainSubstrByWidth(rest, room);
        }
        target.setSuggestion(rest.isEmpty() ? null : rest);
    }

    private String selectedText() {
        return selected >= 0 && selected < matches.size() ? matches.get(selected) : null;
    }

    private void clampOffset() {
        if (selected < offset) {
            offset = selected;
        } else if (selected >= offset + MAX_SHOWN) {
            offset = selected - MAX_SHOWN + 1;
        }
        offset = Math.max(0, Math.min(offset, Math.max(0, matches.size() - MAX_SHOWN)));
    }

    /** Hides the list and clears any ghost text it had put in the field. */
    public void close() {
        if (target != null) target.setSuggestion(null);
        matches.clear();
        selected = 0;
        offset = 0;
    }

    public boolean isEmpty() {
        return target == null || matches.isEmpty();
    }

    // ---- input ----

    /** Moves the highlight, wrapping at both ends the way the command list does. */
    private void cycle(int by) {
        if (matches.isEmpty()) return;
        selected = Math.floorMod(selected + by, matches.size());
        clampOffset();
        // Guarded rather than assumed: every caller checks isEmpty() first,
        // which implies a target, but the two facts live in different methods
        // and a future third caller would not know that.
        if (target != null) updateGhost(target.getValue());
    }

    private void accept() {
        String selection = selectedText();
        if (selection != null) {
            target.setValue(selection);
            target.moveCursorToEnd(false);
        }
        close();
    }

    /**
     * The keys the list owns while it is open: the arrows move through it, tab
     * and enter take the highlighted entry, escape dismisses it. Anything else
     * answers false so the field and the screen keep their own behaviour.
     */
    public boolean keyPressed(int keyCode) {
        if (isEmpty()) return false;
        switch (keyCode) {
            case KEY_UP -> { cycle(-1); return true; }
            case KEY_DOWN -> { cycle(1); return true; }
            case KEY_TAB, KEY_ENTER, KEY_KP_ENTER -> { accept(); return true; }
            case KEY_ESCAPE -> { close(); return true; }
            default -> { return false; }
        }
    }

    /** Scrolling over an open list moves through it rather than through what is behind it. */
    public boolean mouseScrolled(double amount) {
        if (isEmpty() || amount == 0.0) return false;
        cycle(amount > 0.0 ? -1 : 1);
        return true;
    }

    /** Takes whichever row was clicked. */
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (isEmpty()) return false;
        int row = (int) ((mouseY - popupTop()) / LINE_HEIGHT);
        int index = offset + row;
        int w = width();
        int x = popupX(w);
        if (row < 0 || row >= shownCount() || index >= matches.size()
                || mouseX < x || mouseX > x + w) {
            return false;
        }
        selected = index;
        accept();
        return true;
    }

    // ---- drawing ----

    /**
     * Draws the completion list.
     *
     * <p>Called from the panel's content pass, which runs after every widget
     * has drawn itself, so the list is on top without needing to be lifted in
     * z — and lifting in z is the one thing that could not be written once,
     * since the matrix stack changed shape twice in the range.</p>
     */
    public void render(Painter painter) {
        if (isEmpty()) return;
        int top = popupTop();
        int w = width();
        int rows = shownCount();
        int x = popupX(w);

        painter.rect(x, top, w, rows * LINE_HEIGHT, PANEL_COLOR);
        for (int i = 0; i < rows; i++) {
            int index = offset + i;
            painter.flatText(matches.get(index), x + 1, top + 2 + i * LINE_HEIGHT,
                    index == selected ? SELECTED_COLOR : TEXT_COLOR);
        }
    }

    private int shownCount() {
        return Math.min(MAX_SHOWN, matches.size() - offset);
    }

    private int popupTop() {
        return target.getY() + target.getHeight();
    }

    /**
     * Left edge of the list: under its field, but pulled back inside the
     * screen when the entries are wider than the room to the right of it.
     */
    private int popupX(int width) {
        int x = target.getX() - 1;
        if (host == null) return x;
        int limit = host.panelWidth() - MARGIN - width;
        return Math.max(MARGIN, Math.min(x, limit));
    }

    private int width() {
        int longest = 0;
        for (int i = 0; i < shownCount(); i++) {
            longest = Math.max(longest, textWidth(matches.get(offset + i)));
        }
        return longest + 2;
    }

    private int textWidth(String text) {
        return host != null && host.font() != null ? host.font().width(text) : text.length() * 6;
    }
}
