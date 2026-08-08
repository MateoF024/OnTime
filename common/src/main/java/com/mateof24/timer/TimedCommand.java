package com.mateof24.timer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * One command a timer runs, and how long to wait after it.
 *
 * <p>The pause belongs to the command rather than to the timer. A timer that
 * hands out a kit and then teleports needs a beat between those two and
 * nothing between the two scoreboard writes beside them; one figure for the
 * whole timer could only be right for one of those pairs.</p>
 *
 * @param delayTicks ticks to wait <em>after</em> this one before the next in
 *                   the same batch. Zero, which is the default, runs them
 *                   together in the same tick, which is what commands did
 *                   before any of this existed.
 */
public record TimedCommand(String command, int delayTicks) {

    public TimedCommand {
        if (command == null) command = "";
        delayTicks = Math.max(0, delayTicks);
    }

    public static TimedCommand of(String command) {
        return new TimedCommand(command, 0);
    }

    public boolean isBlank() { return command.isBlank(); }

    /**
     * A bare string while nothing waits after it.
     *
     * <p>Which is most of them, and it keeps a file that has never used a
     * delay readable — and identical to what earlier versions wrote.</p>
     */
    public JsonElement toJson() {
        if (delayTicks <= 0) return new JsonPrimitive(command);
        JsonObject json = new JsonObject();
        json.addProperty("command", command);
        json.addProperty("delay", delayTicks);
        return json;
    }

    /** Reads either shape: a bare string is a command with nothing after it. */
    public static TimedCommand fromJson(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonPrimitive()) return of(element.getAsString());
        if (!element.isJsonObject()) return null;
        JsonObject json = element.getAsJsonObject();
        if (!json.has("command")) return null;
        return new TimedCommand(json.get("command").getAsString(),
                json.has("delay") ? json.get("delay").getAsInt() : 0);
    }
}
