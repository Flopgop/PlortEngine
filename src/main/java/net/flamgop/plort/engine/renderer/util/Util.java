package net.flamgop.plort.engine.renderer.util;

import org.jetbrains.annotations.Nullable;

public class Util {
    public static void closeIfNotNull(@Nullable AutoCloseable closeable) {
        try { if (closeable != null) closeable.close(); } catch (Exception _) {}
    }
}
