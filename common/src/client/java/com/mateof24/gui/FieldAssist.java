package com.mateof24.gui;

import net.minecraft.client.gui.components.EditBox;

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
        SOUNDS
    }

    // Taken from CommandSuggestions so the list is indistinguishable from the
    // one chat draws.
    private static final int MAX_SHOWN = 10;
    private static final int LINE_HEIGHT = 12;
    private static final int PANEL_COLOR = 0xD0000000;
    private static final int TEXT_COLOR = 0xFFAAAAAA;
    private static final int SELECTED_COLOR = 0xFFFFFF00;

    private static final int INVALID_TEXT = 0xFF5555;
    private static final int VALID_TEXT = 0xE0E0E0;

    /** Keys handled here, named so the three screen files agree on them. */
    public static final int KEY_UP = 265;
    public static final int KEY_DOWN = 264;
    public static final int KEY_TAB = 258;
    public static final int KEY_ENTER = 257;
    public static final int KEY_KP_ENTER = 335;
    public static final int KEY_ESCAPE = 256;

    private record Field(EditBox box, Predicate<String> valid, Source source, java.util.function.IntSupplier tint) {}

    private final List<Field> fields = new ArrayList<>();
    private final List<String> matches = new ArrayList<>();
    private EditBox target;
    private int selected;
    /** First row drawn, so a long list scrolls with the selection instead of being cut off. */
    private int offset;

    private PanelHost host;

    /** Cached because a registry walk per frame is a walk per frame. */
    private static List<String> soundIds = null;

    /** Forgets every field; call when the screen rebuilds its widgets. */
    public void clear() {
        fields.clear();
        close();
    }

    public void setHost(PanelHost host) {
        this.host = host;
    }

    public void add(EditBox box, Predicate<String> valid, Source source) {
        if (box != null) fields.add(new Field(box, valid, source, null));
    }

    public void add(EditBox box, Predicate<String> valid) {
        add(box, valid, Source.NONE);
    }

    /**
     * A field whose valid text has a colour of its own — the hex colour fields,
     * which read in the colour they name.
     *
     * @param tint the colour to draw valid text in, computed fresh each frame
     */
    public void addTinted(EditBox box, Predicate<String> valid, java.util.function.IntSupplier tint) {
        if (box != null) fields.add(new Field(box, valid, Source.NONE, tint));
    }

    // ---- rules ----

    /** A namespaced id, or a bare path, which is what vanilla accepts too. */
    public static Predicate<String> id() {
        return text -> {
            String value = text.trim();
            if (value.isEmpty()) return false;
            int colon = value.indexOf(':');
            String namespace = colon < 0 ? "minecraft" : value.substring(0, colon);
            String path = colon < 0 ? value : value.substring(colon + 1);
            return !path.isEmpty() && namespace.chars().allMatch(FieldAssist::namespaceChar)
                    && path.chars().allMatch(FieldAssist::pathChar);
        };
    }

    private static boolean namespaceChar(int c) {
        return c == '_' || c == '-' || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.';
    }

    private static boolean pathChar(int c) {
        return namespaceChar(c) || c == '/';
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
            String text = field.box().getValue();
            boolean bad = !text.isBlank() && !field.valid().test(text);
            int good = field.tint() != null ? field.tint().getAsInt() : VALID_TEXT;
            field.box().setTextColor(bad ? INVALID_TEXT : good);
            if (focused == null && field.box().isFocused() && field.source() != Source.NONE) {
                focused = field.box();
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

    /** Moves the highlight to the row the pointer is over, if it is over one. */
    private void hover(int mouseX, int mouseY) {
        if (matches.isEmpty()) return;
        int row = (mouseY - popupTop()) / LINE_HEIGHT;
        int x = target.getX() - 1;
        if (row >= 0 && row < shownCount() && mouseX >= x && mouseX <= x + width()) {
            selected = offset + row;
            clampOffset();
        }
    }

    private void collect(Source source, String text) {
        matches.clear();
        String typed = text.trim().toLowerCase(Locale.ROOT);
        if (typed.isEmpty()) return;

        for (String candidate : candidates(source)) {
            if (candidate.equals(typed)) {
                matches.clear();
                return; // already an exact match; nothing useful left to offer
            }
            if (startsWithLoosely(candidate, typed)) {
                matches.add(candidate);
                if (matches.size() >= MAX_SHOWN * 4) {
                    break; // enough to scroll through; the rest would never be reached
                }
            }
        }
    }

    /**
     * Whether a candidate answers to what has been typed, the way a command
     * argument does: with no namespace given, the path alone is enough.
     */
    private static boolean startsWithLoosely(String candidate, String needle) {
        if (candidate.startsWith(needle)) return true;
        int colon = candidate.indexOf(':');
        return !needle.contains(":") && colon >= 0 && candidate.substring(colon + 1).startsWith(needle);
    }

    private static List<String> candidates(Source source) {
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

    /** Writes the rest of the highlighted entry into the field as ghost text, as commands do. */
    private void updateGhost(String typed) {
        if (target == null) return;
        String selection = selectedText();
        if (selection != null && selection.length() > typed.length()
                && selection.regionMatches(true, 0, typed, 0, typed.length())) {
            target.setSuggestion(selection.substring(typed.length()));
        } else {
            target.setSuggestion(null);
        }
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
        if (row < 0 || row >= shownCount() || index >= matches.size()
                || mouseX < target.getX() - 1 || mouseX > target.getX() - 1 + width()) {
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
        int x = target.getX() - 1;
        int top = popupTop();
        int w = width();
        int rows = shownCount();

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
