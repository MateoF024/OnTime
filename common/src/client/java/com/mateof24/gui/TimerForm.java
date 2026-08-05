package com.mateof24.gui;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One timer's own look and sound, edited.
 *
 * <p>The same twelve settings the server offers as defaults, except these
 * belong to a timer and reach nothing else. {@link SettingsForm} already knows
 * what each one is and how it is typed; this reads them from the timer instead
 * of from the config and sends {@code timer.setDisplay} instead of
 * {@code config.set}.</p>
 *
 * <p>Edits are held until Apply for the same reason as the defaults: half a
 * number is not a setting, and the server rewrites the panel once a second.</p>
 */
public final class TimerForm {

    private final Map<String, String> pending = new LinkedHashMap<>();
    private String timerName = null;

    /**
     * Points the form at a timer, forgetting anything typed for the last one.
     *
     * <p>Carrying edits across a selection change would apply one timer's
     * values to another, which is the one mistake this form must not make.</p>
     */
    public void focus(String name) {
        if (name == null || !name.equals(timerName)) {
            pending.clear();
            timerName = name;
        }
    }

    public String timerName() { return timerName; }

    public void put(String key, String value) { pending.put(key, value); }

    public void discard() { pending.clear(); }

    public int pendingCount() { return pending.size(); }

    /** Whether anything typed actually differs from what the timer holds. */
    public boolean isDirty(AdminModel.TimerRow timer) {
        if (timer == null) return false;
        for (Map.Entry<String, String> entry : pending.entrySet()) {
            SettingsForm.Row row = find(entry.getKey());
            if (row == null) continue;
            if (!entry.getValue().equals(fromTimer(timer, row))) return true;
        }
        return false;
    }

    /** The value to show: what was typed if anything, otherwise the timer's. */
    public String displayed(AdminModel.TimerRow timer, SettingsForm.Row row) {
        String edited = pending.get(row.displayKey());
        return edited != null ? edited : fromTimer(timer, row);
    }

    /** Whether this row differs from the timer's value, for the gutter mark. */
    public boolean isEdited(AdminModel.TimerRow timer, String key) {
        String edited = pending.get(key);
        if (edited == null) return false;
        SettingsForm.Row row = find(key);
        return row != null && !edited.equals(fromTimer(timer, row));
    }

    private static String fromTimer(AdminModel.TimerRow timer, SettingsForm.Row row) {
        JsonObject display = timer == null ? null : timer.display();
        String key = row.displayKey();
        return switch (row.kind()) {
            case INT -> String.valueOf(intOf(display, key, 0));
            case FLOAT -> trimFloat(floatOf(display, key, 0f));
            case STRING -> stringOf(display, key, "");
            case COLOR -> String.format("#%06X", intOf(display, key, 0xFFFFFF) & 0xFFFFFF);
            case BOOL -> "false";
            case PRESET -> stringOf(display, key, "BOSSBAR");
        };
    }

    /** {@code 1.0} rather than {@code 1.0000001}, which is what a field should show. */
    private static String trimFloat(float value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        while (text.contains(".") && text.endsWith("0")) text = text.substring(0, text.length() - 1);
        if (text.endsWith(".")) text = text + "0";
        return text;
    }

    /** The next value of a cycled row, given what is showing. */
    public String cycled(SettingsForm.Row row, String current) {
        int index = SettingsForm.PRESETS.indexOf(current);
        return SettingsForm.PRESETS.get((index + 1) % SettingsForm.PRESETS.size());
    }

    /**
     * The pending edits as {@code timer.setDisplay} requests, one per field.
     *
     * @return one request per accepted edit, and the keys that would not parse
     */
    public SettingsForm.Result build(AdminModel.TimerRow timer) {
        List<JsonObject> requests = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        if (timer == null) return new SettingsForm.Result(requests, rejected);

        for (Map.Entry<String, String> entry : pending.entrySet()) {
            SettingsForm.Row row = find(entry.getKey());
            if (row == null) continue;
            // Unchanged values are not sent: the server would write the same
            // number back and push a snapshot for nothing.
            if (entry.getValue().equals(fromTimer(timer, row))) continue;

            JsonObject args = new JsonObject();
            args.addProperty("name", timer.name());
            args.addProperty("key", row.displayKey());
            try {
                switch (row.kind()) {
                    case INT -> args.addProperty("value", Long.parseLong(entry.getValue().trim()));
                    case FLOAT -> args.addProperty("value", Double.parseDouble(entry.getValue().trim()));
                    case STRING, PRESET -> args.addProperty("value", entry.getValue().trim());
                    case BOOL -> args.addProperty("value", Boolean.parseBoolean(entry.getValue()));
                    case COLOR -> {
                        Integer color = SettingsForm.colorOf(entry.getValue());
                        if (color == null) throw new NumberFormatException(entry.getValue());
                        args.addProperty("value", color);
                    }
                }
            } catch (Exception e) {
                rejected.add(row.displayKey());
                continue;
            }
            requests.add(args);
        }
        return new SettingsForm.Result(requests, rejected);
    }

    private static SettingsForm.Row find(String displayKey) {
        for (SettingsForm.Row row : SettingsForm.displayRows()) {
            if (!row.isHeader() && row.displayKey().equals(displayKey)) return row;
        }
        return null;
    }

    private static int intOf(JsonObject json, String key, int fallback) {
        return has(json, key) ? json.get(key).getAsInt() : fallback;
    }

    private static float floatOf(JsonObject json, String key, float fallback) {
        return has(json, key) ? json.get(key).getAsFloat() : fallback;
    }

    private static String stringOf(JsonObject json, String key, String fallback) {
        return has(json, key) ? json.get(key).getAsString() : fallback;
    }

    private static boolean has(JsonObject json, String key) {
        return json != null && json.has(key) && !json.get(key).isJsonNull();
    }
}
