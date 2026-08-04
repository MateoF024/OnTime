package com.mateof24.command;

import com.mateof24.timer.Audience;
import com.mateof24.timer.TimerRun;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shared formatting for the query commands.
 *
 * <p>An audience, a clock and a run's state are each rendered in more than one
 * place — {@code status}, {@code list} and {@code audience} — and the whole
 * point of those commands is that they agree with each other.</p>
 */
final class CommandFormat {

    private CommandFormat() {}

    /** How many audience members are named before falling back to a count. */
    private static final int MAX_NAMES = 4;

    /**
     * Who an audience reaches: "everyone" for a global one, the player names
     * for a small fixed set, a count beyond that.
     *
     * <p>Offline members are shown by the first segment of their id — a fixed
     * audience keeps its members whether they are connected or not, and hiding
     * them would make {@code status} disagree with what is actually running.</p>
     */
    static Component audience(MinecraftServer server, Audience audience) {
        if (audience.isGlobal()) return Component.translatable("ontime.audience.global");
        int size = audience.size();
        if (size == 0) return Component.translatable("ontime.audience.nobody");
        if (size > MAX_NAMES) return Component.translatable("ontime.audience.count", size);

        List<String> names = new ArrayList<>();
        for (UUID id : audience.players()) {
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(id);
            names.add(player != null ? player.getName().getString() : id.toString().substring(0, 8));
        }
        return Component.literal(String.join(", ", names));
    }

    /** {@code HH:MM:SS} from ticks, always with the hours field. */
    static String duration(long ticks) {
        long totalSeconds = Math.max(0L, ticks) / 20L;
        return String.format("%02d:%02d:%02d",
                totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60);
    }

    /** Running, paused, or which cooldown it is waiting out. */
    static Component runState(TimerRun run) {
        return switch (run.phase()) {
            case REPEAT_COOLDOWN -> Component.translatable("ontime.run.state.repeat_cooldown");
            case SEQUENCE_COOLDOWN -> Component.translatable("ontime.run.state.sequence_cooldown");
            case ACTIVE -> Component.translatable(run.isRunning()
                    ? "ontime.run.state.running" : "ontime.run.state.paused");
        };
    }

    static Component mode(TimerRun run) {
        return Component.translatable(run.mode() == TimerRun.Mode.EACH
                ? "ontime.mode.each" : "ontime.mode.shared");
    }

    /** One {@code label: value} line of a status block. */
    static Component row(String labelKey, Component value) {
        return Component.translatable("ontime.command.status.row",
                Component.translatable(labelKey), value);
    }

    static Component row(String labelKey, String value) {
        return row(labelKey, Component.literal(value));
    }
}
