package com.mateof24.compat;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.CompletableFuture;

/**
 * The vanilla API surface used by common code that drifts across Minecraft
 * versions. There is exactly one implementation of this class per compat
 * source set ({@code common/src/<compatVer>/java}, selected by the root
 * build.gradle) and every implementation must expose the same signatures.
 *
 * <p>compat3 — MC 26.x: {@code PermissionSet}/{@code PermissionCheck}
 * permissions (the int-level API was removed), {@code ResourceLocation}
 * renamed to {@code Identifier}, {@code ResourceKey#location()} renamed to
 * {@code identifier()}.</p>
 */
public final class VanillaCompat {

    private VanillaCompat() {}

    // ------------------------------------------------------------------
    // Permissions (reworked by Mojang in 1.21.11)
    // ------------------------------------------------------------------

    public static boolean hasPermissionLevel(CommandSourceStack source, int level) {
        return source.permissions().hasPermission(
                new Permission.HasCommandLevel(PermissionLevel.byId(level)));
    }

    public static boolean hasPermissionLevel(ServerPlayer player, int level) {
        return player.permissions().hasPermission(
                new Permission.HasCommandLevel(PermissionLevel.byId(level)));
    }

    /**
     * Synthetic command source used to execute a timer's finish command with
     * OP level 4 and "OnTime" as the display name. {@code OWNER} is the
     * PermissionSet equivalent of the old int level 4.
     */
    public static CommandSourceStack createCommandSource(MinecraftServer server, ServerLevel level, String name) {
        return new CommandSourceStack(server, Vec3.ZERO, Vec2.ZERO, level, LevelBasedPermissionSet.OWNER,
                name, Component.literal(name), server, null);
    }

    // ------------------------------------------------------------------
    // Identifiers (ResourceLocation renamed to Identifier in 1.21.11)
    // ------------------------------------------------------------------

    /** Brigadier argument type for namespaced ids ({@code namespace:path}). */
    public static ArgumentType<?> idArgument() {
        return IdentifierArgument.id();
    }

    /** Reads an argument created with {@link #idArgument()} as a plain string. */
    public static String getIdArgument(CommandContext<CommandSourceStack> ctx, String argName) {
        return IdentifierArgument.getId(ctx, argName).toString();
    }

    /** Suggests every registered sound event id. */
    public static CompletableFuture<Suggestions> suggestSoundEvents(SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(BuiltInRegistries.SOUND_EVENT.keySet(), builder);
    }

    /** The {@code namespace:path} id of a server level's dimension. */
    public static String dimensionId(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    /** The {@code namespace:path} id of a registry key. */
    public static String keyId(ResourceKey<?> key) {
        return key.identifier().toString();
    }

    /** A custom packet payload type for the given namespaced id. */
    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String namespace, String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(namespace, path));
    }
}
