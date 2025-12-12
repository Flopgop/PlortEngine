package net.flamgop.borked.renderer.memory;

import net.flamgop.borked.renderer.PlortDevice;
import net.flamgop.borked.renderer.util.VkUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.util.vma.VmaVulkanFunctions;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK14.VK_API_VERSION_1_4;

public class PlortAllocator extends TrackedCloseable {

    private final PlortDevice device;
    private final long handle;

    public PlortAllocator(PlortDevice device) {
        super();
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VmaVulkanFunctions vmaVulkanFunctions = VmaVulkanFunctions.calloc(stack)
                    .set(device.handle().getPhysicalDevice().getInstance(), device.handle());

            VmaAllocatorCreateInfo allocatorInfo = VmaAllocatorCreateInfo.calloc(stack)
                    .device(device.handle())
                    .physicalDevice(device.handle().getPhysicalDevice())
                    .instance(device.handle().getPhysicalDevice().getInstance())
                    .pVulkanFunctions(vmaVulkanFunctions)
                    .vulkanApiVersion(VK_API_VERSION_1_4)
                    .flags(VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT);

            PointerBuffer pAllocator = stack.callocPointer(1);
            VkUtil.check(vmaCreateAllocator(allocatorInfo, pAllocator));
            this.handle = pAllocator.get(0);
        }
    }

    public PlortDevice device() {
        return device;
    }

    public long handle() {
        return this.handle;
    }

    @Override
    public void close() {
        vmaDestroyAllocator(handle);
        super.close();
    }
}
