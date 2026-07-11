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
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.CompletableFuture;

/**
 * The vanilla API surface used by common code that drifts across Minecraft
 * versions. There is exactly one implementation of this class per compat
 * source set ({@code common/src/<compatVer>/java}, selected by the root
 * build.gradle) and every implementation must expose the same signatures.
 *
 * <p>compat1 — MC 1.21.1-1.21.10: int-based permission levels,
 * {@code ResourceLocation} naming.</p>
 */
public final class VanillaCompat {

    private VanillaCompat() {}

    // ------------------------------------------------------------------
    // Permissions (reworked by Mojang in 1.21.11)
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

    // ------------------------------------------------------------------
    // Identifiers (ResourceLocation renamed to Identifier in 1.21.11)
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

    /** A custom packet payload type for the given namespaced id. */
    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String namespace, String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    /**
     * Parses a timer-title spec (4.0.0): tellraw-style JSON component when it
     * looks like JSON, literal text otherwise. Returns null when the JSON is
     * invalid — callers decide the fallback (commands reject, renderers show
     * the raw string). Component.Serializer was already removed within this
     * compat set's range (present in 1.21.1, gone by 1.21.10), so the
     * ComponentSerialization codec with plain JsonOps is used here too —
     * registry-free styles are all a HUD title needs.
     */
    public static Component parseTitle(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String trimmed = raw.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(trimmed);
                return net.minecraft.network.chat.ComponentSerialization.CODEC
                        .parse(com.mojang.serialization.JsonOps.INSTANCE, el)
                        .result().orElse(null);
            } catch (Exception e) {
                return null;
            }
        }
        return Component.literal(raw);
    }
}
