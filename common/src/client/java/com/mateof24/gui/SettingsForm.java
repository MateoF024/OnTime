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
        PRESET
    }

    /**
     * One line of the form. A row with a {@code header} is a section title and
     * has no control.
     */
    public record Row(String header, String key, Kind kind) {

        static Row header(String header) { return new Row(header, null, null); }

        static Row of(String key, Kind kind) { return new Row(null, key, kind); }

        public boolean isHeader() { return header != null; }
    }

    /** Presets in the order the cycle button walks them. */
    public static final List<String> PRESETS = List.of(
            "BOSSBAR", "ACTIONBAR", "TOP_LEFT", "TOP_CENTER", "TOP_RIGHT",
            "CENTER", "BOTTOM_LEFT", "BOTTOM_CENTER", "BOTTOM_RIGHT", "CUSTOM");

    private static final List<Row> ROWS = List.of(
            Row.header("display"),
            Row.of("positionPreset", Kind.PRESET),
            Row.of("timerX", Kind.INT),
            Row.of("timerY", Kind.INT),
            Row.of("timerScale", Kind.FLOAT),

            Row.header("colors"),
            Row.of("colorHigh", Kind.COLOR),
            Row.of("colorMid", Kind.COLOR),
            Row.of("colorLow", Kind.COLOR),
            Row.of("thresholdMid", Kind.INT),
            Row.of("thresholdLow", Kind.INT),

            Row.header("sound"),
            Row.of("timerSoundId", Kind.STRING),
            Row.of("timerSoundVolume", Kind.FLOAT),
            Row.of("timerSoundPitch", Kind.FLOAT),

            Row.header("server"),
            Row.of("maxTimerSeconds", Kind.INT),
            Row.of("commandDelayTicks", Kind.INT),
            Row.of("confirmRunThreshold", Kind.INT),

            Row.header("web"),
            Row.of("webSocketEnabled", Kind.BOOL),
            Row.of("webSocketPort", Kind.INT),
            Row.of("webPanelPort", Kind.INT));

    public static List<Row> rows() { return ROWS; }

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
        };
    }

    /** {@code 1.0} rather than {@code 1.0000001}, which is what a field should show. */
    private static String trimFloat(float value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        while (text.contains(".") && (text.endsWith("0"))) text = text.substring(0, text.length() - 1);
        if (text.endsWith(".")) text = text + "0";
        return text;
    }

    /** The next value of a cycled row, given what is showing. */
    public String cycled(Row row, String current) {
        if (row.kind() == Kind.BOOL) return String.valueOf(!Boolean.parseBoolean(current));
        int index = PRESETS.indexOf(current);
        return PRESETS.get((index + 1) % PRESETS.size());
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

    private static Row find(String key) {
        for (Row row : ROWS) if (!row.isHeader() && row.key().equals(key)) return row;
        return null;
    }
}
