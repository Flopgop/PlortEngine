package net.flamgop.plort.engine.util;

import net.flamgop.plort.engine.renderer.PlortDevice;
import net.flamgop.plort.engine.renderer.pipeline.PlortShaderModule;
import net.flamgop.plort.engine.resource.ResourceIdentifier;
import net.flamgop.plort.engine.resource.ResourceManager;
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
