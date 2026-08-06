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
    public enum Section { QUICK, TITLES, COMMANDS, REPEAT, CONDITIONS }

    /** How one field is edited. */
    public enum Kind {
        TEXT, INT, FLOAT, COLOR, BOOL,
        /** Cycles the position presets. */
        PRESET,
        /** Cycles "finish" and "start" — what a condition does when it is met. */
        ACTION,
        /** Cycles the trigger kinds. */
        TRIGGER
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
                default -> Kind.TEXT;
            };
            out.add(new Field(Section.QUICK, group, "display." + row.displayKey(), kind,
                    "config." + snake(row.key())));
        }

        // ---- everything else ----
        for (String slot : List.of("above", "below", "left", "right")) {
            out.add(new Field(Section.TITLES, "titles", "title." + slot, Kind.TEXT, "title." + slot));
        }

        out.add(new Field(Section.REPEAT, "repeat", "repeat", Kind.BOOL, "repeat"));
        out.add(new Field(Section.REPEAT, "repeat", "repeatCount", Kind.INT, "repeat_count"));
        out.add(new Field(Section.REPEAT, "repeat", "repeatCooldown", Kind.INT, "repeat_cooldown"));
        out.add(new Field(Section.REPEAT, "sequence", "nextTimer", Kind.TEXT, "next_timer"));
        out.add(new Field(Section.REPEAT, "sequence", "sequenceCooldown", Kind.INT, "sequence_cooldown"));

        out.add(new Field(Section.CONDITIONS, "score", "objective", Kind.TEXT, "objective"));
        out.add(new Field(Section.CONDITIONS, "score", "score", Kind.INT, "score"));
        out.add(new Field(Section.CONDITIONS, "score", "target", Kind.TEXT, "target"));
        out.add(new Field(Section.CONDITIONS, "score", "scoreAction", Kind.ACTION, "score_action"));
        out.add(new Field(Section.CONDITIONS, "expression", "expression", Kind.TEXT, "expression"));
        out.add(new Field(Section.CONDITIONS, "expression", "expressionAction",
                Kind.ACTION, "expression_action"));
        out.add(new Field(Section.CONDITIONS, "trigger", "trigger", Kind.TRIGGER, "trigger"));
        out.add(new Field(Section.CONDITIONS, "trigger", "triggerAction", Kind.ACTION, "trigger_action"));
        return List.copyOf(out);
    }

    /**
     * A section's fields with a heading in front of each run of them.
     *
     * <p>Headings are not stored in the table; they are implied by the field
     * that follows, which is what keeps a field and its heading from ever
     * getting out of step.</p>
     */
    public static List<Entry> laidOut(Section section) {
        List<Entry> out = new ArrayList<>();
        String heading = null;
        for (Field field : FIELDS) {
            if (field.section() != section) continue;
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

    public static List<Field> fieldsOf(Section section) {
        List<Field> out = new ArrayList<>();
        for (Field field : FIELDS) if (field.section() == section) out.add(field);
        return out;
    }

    // ---- state ----

    private Section section = Section.TITLES;
    private boolean advanced = false;
    private String timerName = null;
    private boolean creating = false;
    private final Map<String, String> pending = new LinkedHashMap<>();

    /** Opens the editor on an existing timer. */
    public void open(String name) {
        timerName = name;
        creating = false;
        advanced = false;
        section = Section.TITLES;
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
        section = Section.TITLES;
        pending.clear();
        pending.put("hours", "0");
        pending.put("minutes", "1");
        pending.put("seconds", "0");
        pending.put("countUp", "false");
        pending.put("name", "");
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
        return List.of(Section.TITLES, Section.COMMANDS, Section.REPEAT, Section.CONDITIONS);
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

    public boolean isEdited(AdminModel.TimerRow timer, String key) {
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
            case "objective" -> orEmpty(timer.conditionObjective());
            case "score" -> String.valueOf(timer.conditionScore());
            case "target" -> orEmpty(timer.conditionTarget());
            case "scoreAction" -> orDefault(timer.scoreAction(), "finish");
            case "expression" -> orEmpty(timer.conditionExpression());
            case "expressionAction" -> orDefault(timer.expressionAction(), "finish");
            case "trigger" -> orEmpty(timer.triggerType());
            case "triggerAction" -> orDefault(timer.triggerAction(), "finish");
            default -> "";
        };
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

    private static String orEmpty(String value) { return value == null ? "" : value; }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
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
        if (creating) {
            JsonObject args = new JsonObject();
            args.addProperty("name", value(timer, "name"));
            args.addProperty("hours", number(timer, "hours"));
            args.addProperty("minutes", number(timer, "minutes"));
            args.addProperty("seconds", number(timer, "seconds"));
            args.addProperty("countUp", flag(timer, "countUp"));
            ops.add(new Op("timer.create", args));
            return ops;
        }
        if (timer == null) return ops;
        String name = timer.name();

        if (changed(timer, "hours") || changed(timer, "minutes") || changed(timer, "seconds")) {
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

        boolean score = changed(timer, "objective") || changed(timer, "score")
                || changed(timer, "target") || changed(timer, "scoreAction");
        boolean expression = changed(timer, "expression") || changed(timer, "expressionAction");
        if (score || expression) {
            JsonObject args = named(name);
            if (score) {
                args.addProperty("objective", value(timer, "objective"));
                args.addProperty("score", number(timer, "score"));
                args.addProperty("target", value(timer, "target"));
                args.addProperty("scoreAction", value(timer, "scoreAction"));
            }
            if (expression) {
                args.addProperty("expression", value(timer, "expression"));
                args.addProperty("expressionAction", value(timer, "expressionAction"));
            }
            ops.add(new Op("timer.setCondition", args));
        }
        if (changed(timer, "trigger") || changed(timer, "triggerAction")) {
            JsonObject args = named(name);
            args.addProperty("type", value(timer, "trigger"));
            args.addProperty("action", value(timer, "triggerAction"));
            ops.add(new Op("timer.setTrigger", args));
        }
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
