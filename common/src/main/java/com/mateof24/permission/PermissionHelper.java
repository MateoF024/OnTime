package com.mateof24.permission;

import net.minecraft.commands.CommandSourceStack;

public class PermissionHelper {

    private static IPermissionProvider provider = new DefaultPermissionProvider();

    public static void setProvider(IPermissionProvider newProvider) {
        provider = newProvider;
    }

    public static boolean hasPermission(CommandSourceStack source, String node, int fallbackLevel) {
        return provider.hasPermission(source, node, fallbackLevel);
    }

    public interface IPermissionProvider {
        boolean hasPermission(CommandSourceStack source, String node, int fallbackLevel);
    }

    private static class DefaultPermissionProvider implements IPermissionProvider {
        @Override
        public boolean hasPermission(CommandSourceStack source, String node, int fallbackLevel) {
            return source.hasPermission(fallbackLevel);
        }
    }
}