package com.mateof24.permission;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public class PermissionHelper {

    private static IPermissionProvider provider = new DefaultPermissionProvider();

    public static void setProvider(IPermissionProvider newProvider) {
        provider = newProvider;
    }

    public static boolean hasPermission(CommandSourceStack source, String node, int fallbackLevel) {
        // OP/permission gating only applies to players. Non-player sources —
        // server console, command blocks, functions, RCON — always pass, and
        // are never routed through an external IPermissionProvider (command
        // blocks run at vanilla level 2, which the level-4 fallback would
        // otherwise reject).
        if (!(source.getEntity() instanceof ServerPlayer)) return true;
        return provider.hasPermission(source, node, fallbackLevel);
    }

    public interface IPermissionProvider {
        boolean hasPermission(CommandSourceStack source, String node, int fallbackLevel);
    }

    private static class DefaultPermissionProvider implements IPermissionProvider {
        @Override
        public boolean hasPermission(CommandSourceStack source, String node, int fallbackLevel) {
            return com.mateof24.compat.VanillaCompat.hasPermissionLevel(source, fallbackLevel);
        }
    }
}