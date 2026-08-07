package com.mateof24.gui;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the Settings tab shows, and what an edit to it means.
 *
 * <p>No Minecraft types: this is the shape of the form and the rules for
 * turning a typed string into the JSON {@code config.set} expects. The widgets
 * that show it live in {@link AdminPanel}.</p>
 *
 * <p>Edits are held here until they are applied, rather than sent per
 * keystroke. Half a number is not a setting — sending {@code 2} on the way to
 * {@code 25} would briefly make it true — and the server rewrites the panel
 * once a second, so unapplied text has to survive a snapshot landing
 * underneath it.</p>
 */
public final class SettingsForm {

    /** How a value is edited and what JSON type it becomes. */
    public enum Kind {
        /** Whole number, typed. */
        INT,
        /** Decimal, typed. */
        FLOAT,
        /** Free text. */
        STRING,
        /** {@code #RRGGBB}, stored as an integer. */
        COLOR,
        /** Cycled between on and off. */
        BOOL,
        /** Cycled through the position presets. */
        PRESET,
        /** Not a value at all: a button that does something. */
        ACTION
    }

    /**
     * One line of the form. A row with a {@code header} is a section title and
     * has no control.
     */
    public record Row(String header, String key, Kind kind, String displayKey) {

        static Row header(String header) { return new Row(header, null, null, null); }

        static Row of(String key, Kind kind) { return new Row(null, key, kind, null); }

        /** A row that does something rather than holding a value. */
        static Row action(String key) { return new Row(null, key, Kind.ACTION, null); }

        /** The same, for something a timer can do to its own copy. */
        static Row action(String key, String displayKey) {
            return new Row(null, key, Kind.ACTION, displayKey);
        }

        /**
         * A setting that also exists on a timer, and the name it goes by there.
         *
         * <p>The two differ — the server calls it {@code timerScale} because it
         * is the default for every timer, a timer calls it {@code scale}
         * because it is simply its scale — and carrying both here is what lets
         * one row definition serve both forms.</p>
         */
        static Row of(String key, Kind kind, String displayKey) {
            return new Row(null, key, kind, displayKey);
        }

        public boolean isHeader() { return header != null; }

        public boolean isAction() { return kind == Kind.ACTION; }

        /** True when this setting can be given per timer as well. */
        public boolean perTimer() { return displayKey != null; }
    }

    /** Presets in the order the cycle button walks them. */
    public static final List<String> PRESETS = List.of(
            "BOSSBAR", "ACTIONBAR", "TOP_LEFT", "TOP_CENTER", "TOP_RIGHT",
            "CENTER", "BOTTOM_LEFT", "BOTTOM_CENTER", "BOTTOM_RIGHT", "CUSTOM");

    private static final List<Row> ROWS = List.of(
            Row.header("display"),
            Row.of("positionPreset", Kind.PRESET, "preset"),
            // Two numbers nobody can picture. They are still stored and still
            // sent -- the placement screen is what writes them now, and this
            // row is the way in. Shown only when the preset is CUSTOM, because
            // for every other preset the coordinates mean nothing at all.
            Row.action("customPosition", "customPosition"),
            Row.of("timerScale", Kind.FLOAT, "scale"),
            Row.of("hideOnCooldown", Kind.BOOL, "hideOnCooldown"),

            Row.header("colors"),
            Row.of("colorHigh", Kind.COLOR, "colorHigh"),
            Row.of("colorMid", Kind.COLOR, "colorMid"),
            Row.of("colorLow", Kind.COLOR, "colorLow"),
            Row.of("thresholdMid", Kind.INT, "thresholdMid"),
            Row.of("thresholdLow", Kind.INT, "thresholdLow"),

            Row.header("sound"),
            Row.of("timerSoundId", Kind.STRING, "soundId"),
            Row.of("timerSoundVolume", Kind.FLOAT, "soundVolume"),
            Row.of("timerSoundPitch", Kind.FLOAT, "soundPitch"),

            Row.header("server"),
            Row.of("maxTimerSeconds", Kind.INT),
            Row.of("commandDelayTicks", Kind.INT),
            Row.of("confirmRunThreshold", Kind.INT),

            Row.header("web"),
            Row.of("webSocketEnabled", Kind.BOOL),
            Row.of("webSocketPort", Kind.INT),
            Row.of("webPanelPort", Kind.INT),

            // Last, and on its own, because it undoes every row above it.
            Row.header("reset"),
            Row.action("resetDefaults"));

    public static List<Row> rows() { return ROWS; }

    /**
     * The rows to show, given what the position preset is set to.
     *
     * <p>Custom Position is dropped from the list rather than skipped while
     * drawing it: skipping left the row's slot and its label behind, so the
     * form kept a labelled gap where the button used to be.</p>
     */
    public static List<Row> rows(boolean custom) {
        if (custom) return ROWS;
        List<Row> out = new ArrayList<>(ROWS.size());
        for (Row row : ROWS) {
            if (row.isAction() && "customPosition".equals(row.key())) continue;
            out.add(row);
        }
        return out;
    }

    /** The subset a timer owns a copy of, headers included. */
    public static List<Row> displayRows() { return DISPLAY_ROWS; }

    private static final List<Row> DISPLAY_ROWS = buildDisplayRows();

    private static List<Row> buildDisplayRows() {
        List<Row> out = new ArrayList<>();
        String header = null;
        for (Row row : ROWS) {
            if (row.isHeader()) {
                header = row.header();
            } else if (row.perTimer()) {
                // The group heading comes along, but only once something under
                // it turns out to belong to a timer.
                if (header != null) {
                    out.add(Row.header(header));
                    header = null;
                }
                out.add(row);
            }
        }
        return List.copyOf(out);
    }

    /** Edits not yet applied, by key. */
    private final Map<String, String> pending = new LinkedHashMap<>();

    /**
     * Whether anything typed actually differs from what the server holds.
     *
     * <p>Not simply "has something been typed": typing over a value and typing
     * it back is not a change, and Apply lighting up for it would be lying
     * about there being something to send.</p>
     */
    public boolean isDirty(AdminModel model) {
        for (Map.Entry<String, String> entry : pending.entrySet()) {
            Row row = find(entry.getKey());
            if (row == null) continue;
            if (!entry.getValue().equals(fromServer(model, row))) return true;
        }
        return false;
    }

    public int pendingCount() { return pending.size(); }

    public void discard() { pending.clear(); }

    public void put(String key, String value) { pending.put(key, value); }

    /** The value to show: what was typed if anything, otherwise the server's. */
    public String displayed(AdminModel model, Row row) {
        String edited = pending.get(row.key());
        if (edited != null) return edited;
        return fromServer(model, row);
    }

    /**
     * Whether what is typed in this row cannot be used.
     *
     * <p>Asked before anything is sent, not after. A batch with one unusable
     * value used to send the rest and leave that one at whatever the server
     * had, which reads as five settings resetting themselves over a typo in a
     * sixth.</p>
     */
    public boolean isRejected(AdminModel model, String key) {
        String typed = pending.get(key);
        if (typed == null) return false;
        Row row = find(key);
        return row != null && !parses(row, typed);
    }

    /** Every row whose pending text will not parse. */
    public List<String> rejected(AdminModel model) {
        List<String> out = new ArrayList<>();
        for (String key : pending.keySet()) {
            if (isRejected(model, key)) out.add(key);
        }
        return out;
    }

    private static boolean parses(Row row, String typed) {
        String value = typed.trim();
        return switch (row.kind()) {
            case INT -> {
                try {
                    Long.parseLong(value);
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
            case COLOR -> colorOf(value) != null;
            default -> true;
        };
    }

    /** Whether this row's value differs from the server's, for the gutter mark. */
    public boolean isEdited(AdminModel model, String key) {
        String edited = pending.get(key);
        if (edited == null) return false;
        Row row = find(key);
        return row != null && !edited.equals(fromServer(model, row));
    }

    private String fromServer(AdminModel model, Row row) {
        return switch (row.kind()) {
            case INT -> String.valueOf(model.configInt(row.key(), 0));
            case FLOAT -> trimFloat(model.configFloat(row.key(), 0f));
            case STRING -> model.configString(row.key(), "");
            case COLOR -> String.format("#%06X", model.configInt(row.key(), 0xFFFFFF) & 0xFFFFFF);
            case BOOL -> String.valueOf(model.configBool(row.key(), false));
            case PRESET -> model.configString(row.key(), "BOSSBAR");
            // Holds no value, so it can never differ from the server's.
            case ACTION -> "";
        };
    }

    /** {@code 1.0} rather than {@code 1.0000001}, which is what a field should show. */
    private static String trimFloat(float value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        while (text.contains(".") && (text.endsWith("0"))) text = text.substring(0, text.length() - 1);
        if (text.endsWith(".")) text = text + "0";
        return text;
    }

    /** The value one step along a cycled row, forwards or back. */
    public String cycled(Row row, String current, int step) {
        if (row.kind() == Kind.BOOL) return String.valueOf(!Boolean.parseBoolean(current));
        int index = Math.max(0, PRESETS.indexOf(current));
        return PRESETS.get(Math.floorMod(index + step, PRESETS.size()));
    }

    /**
     * The pending edits as {@code config.set} requests.
     *
     * <p>Anything that will not parse is dropped rather than sent: the server
     * would reject it anyway, and it would reject it one message at a time.</p>
     *
     * @return one request per accepted edit, and the keys that would not parse
     */
    public Result build(AdminModel model) {
        List<JsonObject> requests = new ArrayList<>();
        List<String> rejected = new ArrayList<>();

        for (Map.Entry<String, String> entry : pending.entrySet()) {
            Row row = find(entry.getKey());
            if (row == null) continue;
            // Unchanged values are not sent: the server would write the same
            // number back and push a snapshot for nothing.
            if (entry.getValue().equals(fromServer(model, row))) continue;
            JsonObject args = new JsonObject();
            args.addProperty("key", row.key());
            try {
                switch (row.kind()) {
                    case INT -> args.addProperty("value", Long.parseLong(entry.getValue().trim()));
                    case FLOAT -> args.addProperty("value", Double.parseDouble(entry.getValue().trim()));
                    case STRING, PRESET -> args.addProperty("value", entry.getValue().trim());
                    case BOOL -> args.addProperty("value", Boolean.parseBoolean(entry.getValue()));
                    case COLOR -> args.addProperty("value", parseColor(entry.getValue()));
                }
            } catch (Exception e) {
                rejected.add(row.key());
                continue;
            }
            requests.add(args);
        }
        return new Result(requests, rejected);
    }

    public record Result(List<JsonObject> requests, List<String> rejected) {}

    /** The colour a hex field currently means, or null when it means nothing yet. */
    public static Integer colorOf(String text) {
        try {
            return parseColor(text);
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseColor(String text) {
        String hex = text.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() != 6) throw new NumberFormatException(text);
        return Integer.parseInt(hex, 16);
    }

    /** The row a key belongs to, for callers that need to read another row's value. */
    public static Row rowOf(String key) { return find(key); }

    private static Row find(String key) {
        for (Row row : ROWS) if (!row.isHeader() && row.key().equals(key)) return row;
        return null;
    }
}
