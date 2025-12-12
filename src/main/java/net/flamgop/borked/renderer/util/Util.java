package net.flamgop.borked.renderer.util;

import javax.annotation.Nullable;

public class Util {
    public static void closeIfNotNull(@Nullable AutoCloseable closeable) {
        try { if (closeable != null) closeable.close(); } catch (Exception _) {}
    }
}
