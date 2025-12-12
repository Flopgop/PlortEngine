package net.flamgop.borked.renderer.pipeline;

import net.flamgop.borked.renderer.PlortDevice;
import net.flamgop.borked.renderer.memory.TrackedCloseable;
import net.flamgop.borked.renderer.util.ResourceHelper;
import net.flamgop.borked.renderer.util.VkUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkDebugUtilsObjectNameInfoEXT;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.EXTDebugUtils.vkSetDebugUtilsObjectNameEXT;
import static org.lwjgl.vulkan.VK14.*;

public class PlortShaderModule extends TrackedCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlortShaderModule.class);

    public static PlortShaderModule fromResource(PlortDevice device, String spvPath) {
        ByteBuffer shaderCode = ResourceHelper.loadFromResource(spvPath);
        PlortShaderModule module = new PlortShaderModule(device, shaderCode);
        MemoryUtil.memFree(shaderCode);
        return module;
    }

    private final PlortDevice device;
    private final long handle;

    public PlortShaderModule(PlortDevice device, ByteBuffer code) {
        super();
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkShaderModuleCreateInfo moduleInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType$Default()
                    .pCode(code);

            LongBuffer pShaderModule = stack.callocLong(1);
            VkUtil.check(vkCreateShaderModule(device.handle(), moduleInfo, null, pShaderModule));
            handle = pShaderModule.get(0);
        }
        LOGGER.debug("Created shader module with handle {}", String.format("0x%016X", handle));
    }

    public void label(String name) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDebugUtilsObjectNameInfoEXT nameInfo = VkDebugUtilsObjectNameInfoEXT.calloc(stack)
                    .sType$Default()
                    .objectType(VK_OBJECT_TYPE_SHADER_MODULE)
                    .objectHandle(this.handle)
                    .pObjectName(stack.UTF8(name + " Shader Module"));

            vkSetDebugUtilsObjectNameEXT(this.device.handle(), nameInfo);
        }
    }

    public long handle() {
        return handle;
    }

    @Override
    public void close() {
        vkDestroyShaderModule(device.handle(), handle, null);
        super.close();
    }
}
