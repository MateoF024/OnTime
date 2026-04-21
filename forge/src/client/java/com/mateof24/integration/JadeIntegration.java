package com.mateof24.integration;

public class JadeIntegration {

    private static volatile Boolean installed = null;
    private static volatile int lastOverlayBottom = 0;

    public static boolean isInstalled() {
        if (installed == null) {
            try {
                Class.forName("snownee.jade.Jade");
                installed = true;
            } catch (ClassNotFoundException e) {
                installed = false;
            }
        }
        return installed;
    }

    public static void reportRenderedBottom(int bottomY) {
        lastOverlayBottom = bottomY;
    }

    public static int getLastRenderedBottom() {
        if (!isInstalled()) return 0;
        return lastOverlayBottom;
    }
}