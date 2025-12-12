package net.flamgop.borked.renderer.image;

import net.flamgop.borked.renderer.PlortDevice;
import net.flamgop.borked.renderer.memory.TrackedCloseable;
import net.flamgop.borked.renderer.util.VkUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDebugUtilsObjectNameInfoEXT;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.EXTDebugUtils.vkSetDebugUtilsObjectNameEXT;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_FILTER_LINEAR;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT;

public class PlortSampler extends TrackedCloseable {

    public enum Filter {
        NEAREST(VK_FILTER_NEAREST),
        LINEAR(VK_FILTER_LINEAR),

        ;
        final int vkQualifier;
        Filter(int vkQualifier) {
            this.vkQualifier = vkQualifier;
        }
        public int qualifier() {
            return vkQualifier;
        }
    }

    public enum AddressMode {
        REPEAT(VK_SAMPLER_ADDRESS_MODE_REPEAT),
        MIRRORED_REPEAT(VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT),
        CLAMP_TO_EDGE(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE),
        CLAMP_TO_BORDER(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER),

        ;
        final int vkQualifier;
        AddressMode(int vkQualifier) {
            this.vkQualifier = vkQualifier;
        }
        public int qualifier() {
            return vkQualifier;
        }
    }

    private final PlortDevice device;
    private final long handle;

    public PlortSampler(
            PlortDevice device,
            Filter minFilter, Filter magFilter,
            AddressMode addressModeU, AddressMode addressModeV, AddressMode addressModeW
    ) {
        super();
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType$Default()
                    .magFilter(magFilter.qualifier())
                    .minFilter(minFilter.qualifier())
                    .addressModeU(addressModeU.qualifier())
                    .addressModeV(addressModeV.qualifier())
                    .addressModeW(addressModeW.qualifier());

            LongBuffer pSampler = stack.callocLong(1);
            VkUtil.check(vkCreateSampler(device.handle(), samplerInfo, null, pSampler));
            this.handle = pSampler.get(0);
        }
    }

    public void label(String name) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDebugUtilsObjectNameInfoEXT nameInfo = VkDebugUtilsObjectNameInfoEXT.calloc(stack)
                    .sType$Default()
                    .objectType(VK_OBJECT_TYPE_SAMPLER)
                    .objectHandle(this.handle)
                    .pObjectName(stack.UTF8(name + " Sampler"));

            vkSetDebugUtilsObjectNameEXT(this.device.handle(), nameInfo);
        }
    }

    public long handle() {
        return handle;
    }

    public void info(VkDescriptorImageInfo info) {
        info.sampler(this.handle());
    }

    @Override
    public void close() {
        vkDestroySampler(device.handle(), handle, null);
        super.close();
    }
}
