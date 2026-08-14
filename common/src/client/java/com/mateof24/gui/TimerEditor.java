package com.mateof24.gui;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything one timer is, on one screen.
 *
 * <p>The commands could already do all of this, one flag at a time, and that
 * was the ceiling: {@code /timer repeat}, {@code /timer commands add},
 * {@code /timer condition}, each its own line, each needing the name again.
 * A screen does not have that constraint, so this does not copy the commands —
 * it puts the whole timer in front of you at once, in six groups, and sends
 * only what actually changed.</p>
 *
 * <p>Six groups rather than one long page because thirty-odd fields on one
 * page is a scroll bar and a hunt. The rail says what a timer <em>has</em>
 * before you have read a single field.</p>
 *
 * <p>No Minecraft types: this is the shape of the editor and the rules for
 * turning what was typed into operations. {@link AdminPanel} draws it.</p>
 */
public final class TimerEditor {

    /**
     * Where a field lives.
     *
     * <p>{@link #QUICK} is the column beside the list: what gets changed often,
     * and what can be changed without leaving the list you are working through.
     * The rest are the pages of the advanced editor, which holds everything
     * else a timer can do.</p>
     */
    public enum Section { QUICK, TITLES, COMMANDS, REPEAT, TRIGGERS }

    /** How one field is edited. */
    public enum Kind {
        TEXT, INT, FLOAT, COLOR, BOOL,
        /** Cycles the position presets. */
        PRESET,
        /** Cycles "finish" and "start" — what a condition does when it is met. */
        ACTION,
        /** Cycles the trigger kinds. */
        TRIGGER,
        /** Not a value: a button that opens the placement screen. */
        PICKER
    }

    /**
     * One editable field.
     *
     * <p>{@code key} is what the pending map and the operations use;
     * {@code group} is the heading it sits under, because eighteen fields in a
     * column with nothing between them is a wall.</p>
     */
    public record Field(Section section, String group, String key, Kind kind, String label) {}

    /** Either a heading or a field, never both. */
    public record Entry(String heading, Field field) {

        public boolean isHeading() { return heading != null; }
    }

    /** What a condition or a trigger does when it fires. */
    public static final List<String> ACTIONS = List.of("finish", "start");

    /** The trigger kinds the server understands, plus "off". */
    public static final List<String> TRIGGERS = List.of("", "join", "leave", "death", "respawn");

    private static final List<Field> FIELDS = buildFields();

    private static List<Field> buildFields() {
        List<Field> out = new ArrayList<>();
        // ---- the column beside the list ----
        out.add(new Field(Section.QUICK, "identity", "name", Kind.TEXT, "name"));
        out.add(new Field(Section.QUICK, "identity", "hours", Kind.INT, "hours"));
        out.add(new Field(Section.QUICK, "identity", "minutes", Kind.INT, "minutes"));
        out.add(new Field(Section.QUICK, "identity", "seconds", Kind.INT, "seconds"));
        out.add(new Field(Section.QUICK, "identity", "countUp", Kind.BOOL, "direction"));
        out.add(new Field(Section.QUICK, "identity", "silent", Kind.BOOL, "silent"));
        out.add(new Field(Section.QUICK, "identity", "finishCommand", Kind.TEXT, "finish_command"));

        // The twelve a timer owns a copy of, under the same three headings the
        // settings tab gives the defaults: the same things, so the same shape.
        String group = "display";
        for (SettingsForm.Row row : SettingsForm.displayRows()) {
            if (row.isHeader()) {
                group = row.header();
                continue;
            }
            Kind kind = switch (row.kind()) {
                case INT -> Kind.INT;
                case FLOAT -> Kind.FLOAT;
                case COLOR -> Kind.COLOR;
                case PRESET -> Kind.PRESET;
                case ACTION -> Kind.PICKER;
                // Without this a yes-or-no fell through to a text box here
                // while the settings tab drew the same setting as a button.
                // One setting, two controls, and one of them let you type
                // anything at all into a field with two possible values.
                case BOOL -> Kind.BOOL;
                default -> Kind.TEXT;
            };
            out.add(new Field(Section.QUICK, group, "display." + row.displayKey(), kind,
                    "config." + snake(row.key())));
        }

        // ---- everything else ----
        // Titles, repeating and handing over are values like any other, and
        // they sit with the rest of the timer's values rather than behind a
        // second screen. What is left in the advanced editor is the two things
        // that are lists rather than forms.
        for (String slot : List.of("above", "below", "left", "right")) {
            out.add(new Field(Section.QUICK, "titles", "title." + slot, Kind.TEXT, "title." + slot));
        }

        out.add(new Field(Section.QUICK, "repeat", "repeat", Kind.BOOL, "repeat"));
        out.add(new Field(Section.QUICK, "repeat", "repeatCount", Kind.INT, "repeat_count"));
        out.add(new Field(Section.QUICK, "repeat", "repeatCooldown", Kind.INT, "repeat_cooldown"));
        out.add(new Field(Section.QUICK, "sequence", "nextTimer", Kind.TEXT, "next_timer"));
        out.add(new Field(Section.QUICK, "sequence", "sequenceCooldown", Kind.INT, "sequence_cooldown"));

        // Nothing for TRIGGERS: it is a list, not a form, so AdminPanel draws
        // it the way it draws the command list.
        return out;
    }

    /**
     * A section's fields with a heading in front of each run of them.
     *
     * <p>Headings are not stored in the table; they are implied by the field
     * that follows, which is what keeps a field and its heading from ever
     * getting out of step.</p>
     */
    public static List<Entry> laidOut(Section section) {
        return laidOut(section, false, false);
    }

    /**
     * The rows of a section.
     *
     * <p>{@code finishCommand} exists only while creating. It is not a field a
     * timer has — the timer has a list of commands, and the editor's Commands
     * page is where that list is kept. Asking here saves the one thing you
     * always know at creation from needing a second visit.</p>
     */
    public static List<Entry> laidOut(Section section, boolean creating, boolean custom) {
        List<Entry> out = new ArrayList<>();
        String heading = null;
        for (Field field : FIELDS) {
            if (field.section() != section) continue;
            if (!creating && "finishCommand".equals(field.key())) continue;
            // Dropped from the list, not skipped while drawing: skipping left
            // the row's slot and its label behind.
            if (!custom && "display.customPosition".equals(field.key())) continue;
            if (!field.group().equals(heading)) {
                heading = field.group();
                out.add(new Entry(heading, null));
            }
            out.add(new Entry(null, field));
        }
        return out;
    }

    /** {@code timerSoundId} to {@code timer_sound_id}, which is how the keys read. */
    static String snake(String camel) {
        StringBuilder out = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) out.append('_').append(Character.toLowerCase(c));
            else out.append(c);
        }
        return out.toString();
    }

    public static List<Field> fields() { return FIELDS; }

    /** The field a key belongs to, for callers that need another field's value. */
    public static Field fieldOf(String key) {
        for (Field field : FIELDS) if (field.key().equals(key)) return field;
        return null;
    }

    public static List<Field> fieldsOf(Section section) {
        List<Field> out = new ArrayList<>();
        for (Field field : FIELDS) if (field.section() == section) out.add(field);
        return out;
    }

    // ---- state ----

    private Section section = Section.COMMANDS;
    private boolean advanced = false;
    private String timerName = null;
    private boolean creating = false;
    private final Map<String, String> pending = new LinkedHashMap<>();

    /** Opens the editor on an existing timer. */
    public void open(String name) {
        timerName = name;
        creating = false;
        advanced = false;
        section = Section.COMMANDS;
        pending.clear();
    }

    /**
     * Opens it on a timer that does not exist yet.
     *
     * <p>Only the basics can be filled in: everything else is a property of a
     * timer, and there is not one yet. Creating reopens the editor on the real
     * thing, where the other five groups mean something.</p>
     */
    public void openNew() {
        timerName = null;
        creating = true;
        advanced = false;
        section = Section.COMMANDS;
        pending.clear();
        pending.put("hours", "0");
        pending.put("minutes", "1");
        pending.put("seconds", "0");
        pending.put("countUp", "false");
        pending.put("name", "");
        pending.put("finishCommand", "");
    }

    public void close() {
        timerName = null;
        creating = false;
        advanced = false;
        pending.clear();
    }

    public boolean isOpen() { return creating || timerName != null; }

    public boolean isCreating() { return creating; }

    public String timerName() { return timerName; }

    public Section section() { return section; }

    public void setSection(Section section) { this.section = section; }

    /** The pages the advanced editor offers: everything that is not QUICK. */
    public static List<Section> advancedSections() {
        return List.of(Section.COMMANDS, Section.TRIGGERS);
    }

    /** True while the advanced editor is the thing on screen. */
    public boolean advanced() { return advanced; }

    public void setAdvanced(boolean advanced) { this.advanced = advanced; }

    public void put(String key, String value) { pending.put(key, value); }

    public void discard() { pending.clear(); }

    public int pendingCount() { return pending.size(); }

    public boolean isDirty(AdminModel.TimerRow timer) {
        if (creating) return true;
        for (Map.Entry<String, String> entry : pending.entrySet()) {
            if (!entry.getValue().equals(stored(timer, entry.getKey()))) return true;
        }
        return false;
    }

    /** The value to show: what was typed if anything, otherwise the timer's. */
    public String displayed(AdminModel.TimerRow timer, Field field) {
        String edited = pending.get(field.key());
        return edited != null ? edited : stored(timer, field.key());
    }

    /**
     * Whether what is typed in this field cannot be used.
     *
     * <p>Checked before anything is sent rather than after. Applying a batch
     * where one value is nonsense used to send the rest and leave that one at
     * whatever the server already had, which reads as five settings resetting
     * themselves because of a typo in a sixth.</p>
     */
    public boolean isRejected(AdminModel.TimerRow timer, Field field) {
        String typed = pending.get(field.key());
        if (typed == null) return false;
        return !parses(field, typed);
    }

    /** Every field whose pending text will not parse. */
    public List<String> rejected(AdminModel.TimerRow timer) {
        List<String> out = new ArrayList<>();
        for (Field field : FIELDS) {
            if (isRejected(timer, field)) out.add(field.key());
        }
        return out;
    }

    private static boolean parses(Field field, String typed) {
        String value = typed.trim();
        return switch (field.kind()) {
            case INT -> {
                try {
                    Integer.parseInt(value);
                    yield true;
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            case FLOAT -> {
                try {
                    Double.parseDouble(value);
                    yield true;
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            case COLOR -> SettingsForm.colorOf(value) != null;
            // A name is the one piece of text with a shape the server insists
            // on, and it is the one the server cannot fix for you.
            case TEXT -> !"name".equals(field.key())
                    || value.matches("[A-Za-z0-9_.+-]{1,32}");
            default -> true;
        };
    }

    /**
     * Whether a field has been changed from what the timer holds.
     *
     * <p>Never while creating: there is no timer yet, so there is nothing for
     * a value to differ from. The form is seeded with the server defaults and
     * every one of them came up marked as an edit, which said the opposite of
     * what is true — those are the values a new timer is made with.</p>
     */
    public boolean isEdited(AdminModel.TimerRow timer, String key) {
        if (creating) return false;
        String edited = pending.get(key);
        return edited != null && !edited.equals(stored(timer, key));
    }

    /** The value one step along a cycled field, forwards or back. */
    public String cycled(Field field, String current, int step) {
        return switch (field.kind()) {
            case BOOL -> String.valueOf(!Boolean.parseBoolean(current));
            case PRESET -> next(SettingsForm.PRESETS, current, step);
            case ACTION -> next(ACTIONS, current, step);
            case TRIGGER -> next(TRIGGERS, current, step);
            default -> current;
        };
    }

    private static String next(List<String> values, String current, int step) {
        int index = Math.max(0, values.indexOf(current));
        return values.get(Math.floorMod(index + step, values.size()));
    }

    // ---- reading the timer ----

    private String stored(AdminModel.TimerRow timer, String key) {
        if (timer == null) return defaultFor(key);
        if (key.startsWith("display.")) {
            return displayValue(timer, key.substring("display.".length()));
        }
        if (key.startsWith("title.")) {
            return timer.title(key.substring("title.".length()));
        }
        return switch (key) {
            case "name" -> timer.name();
            case "hours" -> String.valueOf(timer.targetTicks() / 20L / 3600L);
            case "minutes" -> String.valueOf(timer.targetTicks() / 20L % 3600L / 60L);
            case "seconds" -> String.valueOf(timer.targetTicks() / 20L % 60L);
            case "countUp" -> String.valueOf(timer.countUp());
            case "silent" -> String.valueOf(timer.silent());
            case "repeat" -> String.valueOf(timer.repeat());
            case "repeatCount" -> String.valueOf(timer.repeatCount());
            case "repeatCooldown" -> String.valueOf(timer.repeatCooldownTicks() / 20L);
            case "nextTimer" -> timer.nextTimer() == null ? "" : timer.nextTimer();
            case "sequenceCooldown" -> String.valueOf(timer.sequenceCooldownTicks() / 20L);
            default -> "";
        };
    }

    /**
     * One field by name, for a page that draws a field of its own.
     *
     * <p>The commands page is a list, so it is built by hand rather than from
     * a section -- and the one value on it still has to be the same field
     * that everything else already knows how to read and apply.</p>
     */
    public static Field fieldFor(String key) {
        for (Field field : FIELDS) {
            if (field.key().equals(key)) return field;
        }
        return null;
    }

    private static String defaultFor(String key) {
        return switch (key) {
            case "hours", "seconds", "score", "repeatCooldown", "sequenceCooldown" -> "0";
            case "minutes" -> "1";
            case "repeatCount" -> "-1";
            case "countUp", "silent", "repeat" -> "false";
            case "scoreAction", "expressionAction", "triggerAction" -> "finish";
            default -> "";
        };
    }

    private static String displayValue(AdminModel.TimerRow timer, String key) {
        JsonObject display = timer.display();
        if (display == null || !display.has(key) || display.get(key).isJsonNull()) return "";
        return switch (key) {
            case "colorHigh", "colorMid", "colorLow" ->
                    String.format("#%06X", display.get(key).getAsInt() & 0xFFFFFF);
            case "scale", "soundVolume", "soundPitch" -> trimFloat(display.get(key).getAsFloat());
            default -> display.get(key).getAsString();
        };
    }

    private static String trimFloat(float value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        while (text.contains(".") && text.endsWith("0")) text = text.substring(0, text.length() - 1);
        if (text.endsWith(".")) text = text + "0";
        return text;
    }

    // ---- turning edits into operations ----

    /** One operation and its arguments, ready to send. */
    public record Op(String name, JsonObject args) {}

    /**
     * What has to be sent for the pending edits to be true.
     *
     * <p>Grouped rather than one message per field: the server takes a whole
     * repeat block or a whole condition at once, and sending them apart would
     * mean a moment where a count applied to a timer that was not repeating
     * yet. Anything untouched is left out entirely.</p>
     */
    public List<Op> build(AdminModel.TimerRow timer) {
        List<Op> ops = new ArrayList<>();
        String name;
        if (creating) {
            name = value(timer, "name");
            JsonObject args = new JsonObject();
            args.addProperty("name", name);
            args.addProperty("hours", number(timer, "hours"));
            args.addProperty("minutes", number(timer, "minutes"));
            args.addProperty("seconds", number(timer, "seconds"));
            args.addProperty("countUp", flag(timer, "countUp"));
            ops.add(new Op("timer.create", args));

            // Its first finish command, if one was typed. Left blank the timer
            // runs nothing, which is a timer the mod now allows.
            String finish = value(timer, "finishCommand");
            if (finish != null && !finish.isBlank()) {
                JsonObject command = new JsonObject();
                command.addProperty("name", name);
                command.addProperty("command", finish.trim());
                ops.add(new Op("timer.addCommand", command));
            }
            // And on, rather than back. The creation form draws every field a
            // timer has, and this used to return here with only four of them
            // sent: colours, titles, repeating and the rest were asked for,
            // typed in, and dropped without a word. They are applied to the
            // timer that now exists, in the order the list is sent.
            //
            // "changed" against a timer that does not exist compares with what
            // a new one copies from the settings, so what goes is exactly what
            // was moved off the default.
        } else {
            if (timer == null) return ops;
            name = timer.name();
        }

        // Already carried by timer.create, where it is not optional.
        if (!creating
                && (changed(timer, "hours") || changed(timer, "minutes") || changed(timer, "seconds"))) {
            JsonObject args = named(name);
            args.addProperty("hours", number(timer, "hours"));
            args.addProperty("minutes", number(timer, "minutes"));
            args.addProperty("seconds", number(timer, "seconds"));
            ops.add(new Op("timer.setTime", args));
        }
        if (changed(timer, "silent")) {
            JsonObject args = named(name);
            args.addProperty("silent", flag(timer, "silent"));
            ops.add(new Op("timer.setSilent", args));
        }

        for (SettingsForm.Row row : SettingsForm.displayRows()) {
            if (row.isHeader()) continue;
            String key = "display." + row.displayKey();
            if (!changed(timer, key)) continue;
            JsonObject args = named(name);
            args.addProperty("key", row.displayKey());
            String typed = value(timer, key);
            switch (row.kind()) {
                case BOOL -> args.addProperty("value", Boolean.parseBoolean(typed.trim()));
                case INT -> args.addProperty("value", (long) numberOf(typed));
                case FLOAT -> args.addProperty("value", decimalOf(typed));
                case COLOR -> {
                    Integer color = SettingsForm.colorOf(typed);
                    if (color == null) continue;
                    args.addProperty("value", color);
                }
                default -> args.addProperty("value", typed.trim());
            }
            ops.add(new Op("timer.setDisplay", args));
        }

        for (String slot : List.of("above", "below", "left", "right")) {
            if (!changed(timer, "title." + slot)) continue;
            JsonObject args = named(name);
            args.addProperty("slot", slot);
            args.addProperty("text", value(timer, "title." + slot));
            ops.add(new Op("timer.setTitle", args));
        }

        if (changed(timer, "repeat") || changed(timer, "repeatCount") || changed(timer, "repeatCooldown")) {
            JsonObject args = named(name);
            args.addProperty("repeat", flag(timer, "repeat"));
            args.addProperty("count", number(timer, "repeatCount"));
            args.addProperty("cooldownSeconds", number(timer, "repeatCooldown"));
            ops.add(new Op("timer.setRepeat", args));
        }
        if (changed(timer, "nextTimer") || changed(timer, "sequenceCooldown")) {
            JsonObject args = named(name);
            args.addProperty("next", value(timer, "nextTimer"));
            args.addProperty("cooldownSeconds", number(timer, "sequenceCooldown"));
            ops.add(new Op("timer.setSequence", args));
        }

        // Triggers are not sent from here. They are a list, added and removed
        // as they are edited, exactly like the commands -- the two operations
        // this replaced each overwrote whatever was there.
        return ops;
    }

    private static JsonObject named(String name) {
        JsonObject args = new JsonObject();
        args.addProperty("name", name);
        return args;
    }

    private boolean changed(AdminModel.TimerRow timer, String key) {
        String edited = pending.get(key);
        return edited != null && !edited.equals(stored(timer, key));
    }

    private String value(AdminModel.TimerRow timer, String key) {
        String edited = pending.get(key);
        return (edited != null ? edited : stored(timer, key)).trim();
    }

    private int number(AdminModel.TimerRow timer, String key) {
        return numberOf(value(timer, key));
    }

    private boolean flag(AdminModel.TimerRow timer, String key) {
        return Boolean.parseBoolean(value(timer, key));
    }

    private static int numberOf(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double decimalOf(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return 0d;
        }
    }
}
