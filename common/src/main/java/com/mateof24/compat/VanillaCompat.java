package com.mateof24.compat;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.CompletableFuture;

/**
 * The vanilla API surface used by common code that drifts across Minecraft
 * versions. On 'main' there is one implementation per compat source set,
 * selected by the root build.gradle from seven families; this branch has one
 * family and therefore one implementation, which is why it sits in
 * {@code src/main} with no version axis above it.
 *
 * <p>1.20.1. Every signature here is the same as the one 'main' exposes, so
 * everything above this class is identical on both branches. Two things could
 * not be:</p>
 *
 * <ul>
 *   <li>{@code payloadType} is not here at all. {@code CustomPacketPayload}
 *       does not exist in 1.20.1 — verified against the mapped jar, the class
 *       is simply absent — and the networking goes over raw channels instead.
 *       Nothing outside the network package ever called it.</li>
 *   <li>{@link #parseTitle} reads its JSON through {@code Component.Serializer}
 *       rather than {@code ComponentSerialization.CODEC}. The codec does not
 *       exist yet here and the serializer does; on 'main' it is the other way
 *       round, and the comment there says so.</li>
 * </ul>
 */
public final class VanillaCompat {

    private VanillaCompat() {}

    // ------------------------------------------------------------------
    // Permissions
    // ------------------------------------------------------------------

    public static boolean hasPermissionLevel(CommandSourceStack source, int level) {
        return source.hasPermission(level);
    }

    public static boolean hasPermissionLevel(ServerPlayer player, int level) {
        return player.hasPermissions(level);
    }

    /**
     * Synthetic command source used to execute a timer's finish command with
     * OP level 4 and "OnTime" as the display name.
     */
    public static CommandSourceStack createCommandSource(MinecraftServer server, ServerLevel level, String name) {
        return new CommandSourceStack(server, Vec3.ZERO, Vec2.ZERO, level, 4,
                name, Component.literal(name), server, null);
    }

    /**
     * Same OP level, but executed as the player and from where they stand, so
     * a per-player timer's command can use {@code @s} and relative
     * coordinates and mean the person the run belongs to.
     */
    public static CommandSourceStack createPlayerCommandSource(MinecraftServer server,
                                                               net.minecraft.server.level.ServerPlayer player) {
        // Entity.level() returns Level here, so the cast is needed -- which is
        // also the form 'main' settles on, for a different reason: there the
        // accessor drifts within one compat set.
        return new CommandSourceStack(server, player.position(), player.getRotationVector(),
                (ServerLevel) player.level(), 4, player.getName().getString(), player.getDisplayName(),
                server, player);
    }

    // ------------------------------------------------------------------
    // Identifiers
    // ------------------------------------------------------------------

    /** Brigadier argument type for namespaced ids ({@code namespace:path}). */
    public static ArgumentType<?> idArgument() {
        return ResourceLocationArgument.id();
    }

    /** Reads an argument created with {@link #idArgument()} as a plain string. */
    public static String getIdArgument(CommandContext<CommandSourceStack> ctx, String argName) {
        return ResourceLocationArgument.getId(ctx, argName).toString();
    }

    /** Suggests every registered sound event id. */
    public static CompletableFuture<Suggestions> suggestSoundEvents(SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(BuiltInRegistries.SOUND_EVENT.keySet(), builder);
    }

    /** The {@code namespace:path} id of a server level's dimension. */
    public static String dimensionId(ServerLevel level) {
        return level.dimension().location().toString();
    }

    /** The {@code namespace:path} id of a registry key. */
    public static String keyId(ResourceKey<?> key) {
        return key.location().toString();
    }

    /**
     * Parses a timer-title spec: tellraw-style JSON component when it looks
     * like JSON, literal text otherwise. Returns null when the JSON is invalid
     * — callers decide the fallback (commands reject, renderers show the raw
     * string).
     */
    public static Component parseTitle(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String trimmed = raw.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                // The string overload, not the JsonElement one. Both exist here,
                // but the element form cannot be resolved against the remapped
                // 1.20.1 jar, and this is the form the branch has always
                // compiled. Malformed JSON throws either way and the catch is
                // what turns it into null.
                return Component.Serializer.fromJson(trimmed);
            } catch (Exception e) {
                return null;
            }
        }
        return Component.literal(raw);
    }
}
