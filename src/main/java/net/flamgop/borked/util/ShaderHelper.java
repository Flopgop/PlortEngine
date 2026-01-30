package net.flamgop.borked.util;

import net.flamgop.borked.renderer.PlortDevice;
import net.flamgop.borked.renderer.pipeline.PlortShaderModule;
import net.flamgop.borked.resource.ResourceIdentifier;
import net.flamgop.borked.resource.ResourceManager;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class ShaderHelper {
    public static PlortShaderModule load(PlortDevice device, ResourceManager manager, ResourceIdentifier identifier) {
        try (InputStream stream = manager.open(identifier)) {
            byte[] bytes = stream.readAllBytes();
            ByteBuffer code = MemoryUtil.memAlloc(bytes.length);
            code.put(bytes);
            code.flip();
            PlortShaderModule module = new PlortShaderModule(device, code);
            MemoryUtil.memFree(code);
            return module;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
