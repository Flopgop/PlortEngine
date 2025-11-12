package net.flamgop.borked.engine.util;

import net.flamgop.borked.Main;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class ResourceHelper {
    public static ByteBuffer loadFromResource(String resourcePath) {
        try (InputStream stream = Main.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) throw new RuntimeException("Couldn't open resource stream for " + resourcePath);
            byte[] bytes = stream.readAllBytes();
            ByteBuffer mem = MemoryUtil.memAlloc(bytes.length);
            mem.put(bytes);
            mem.flip();
            return mem;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
