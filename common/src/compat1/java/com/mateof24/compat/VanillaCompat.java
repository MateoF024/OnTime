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
}
