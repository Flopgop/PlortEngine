package net.flamgop.borked.renderer.memory;

import net.flamgop.borked.renderer.util.Formats;
import net.flamgop.borked.renderer.util.VkUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferDeviceAddressInfo;
import org.lwjgl.vulkan.VkDebugUtilsObjectNameInfoEXT;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.LongBuffer;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.EXTDebugUtils.vkSetDebugUtilsObjectNameEXT;
import static org.lwjgl.vulkan.VK14.*;

public class PlortBuffer extends TrackedCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlortBuffer.class);

    private final PlortAllocator allocator;

    private final long size;
    private final long handle;
    private final long memory;

    private final long deviceAddress;

    public PlortBuffer(long size, int usageFlags, PlortAllocator allocator) {
        super();
        this.size = size;
        this.allocator = allocator;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(usageFlags | BufferUsage.SHADER_DEVICE_ADDRESS_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            VmaAllocationCreateInfo allocInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(VMA_MEMORY_USAGE_CPU_TO_GPU);

            LongBuffer pBuffer = stack.callocLong(1);
            PointerBuffer pAllocation = stack.callocPointer(1);
            VkUtil.check(vmaCreateBuffer(allocator.handle(), bufferInfo, allocInfo, pBuffer, pAllocation, null));
            this.handle = pBuffer.get(0);
            this.memory = pAllocation.get(0);

            VkBufferDeviceAddressInfo addressInfo = VkBufferDeviceAddressInfo.calloc(stack)
                    .sType$Default()
                    .buffer(this.handle);

            this.deviceAddress = vkGetBufferDeviceAddress(allocator.device().handle(), addressInfo);
            if (deviceAddress == 0x0) {
                LOGGER.warn("PlortBuffer {} has a null device address!", Formats.address(handle));
            }
        }
    }

    public void label(String name) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDebugUtilsObjectNameInfoEXT nameInfo = VkDebugUtilsObjectNameInfoEXT.calloc(stack)
                    .sType$Default()
                    .objectType(VK_OBJECT_TYPE_BUFFER)
                    .objectHandle(this.handle)
                    .pObjectName(stack.UTF8(name + " Buffer"));

            vkSetDebugUtilsObjectNameEXT(this.allocator.device().handle(), nameInfo);
        }
    }

    public void map(PointerBuffer dstMemory) {
        vmaMapMemory(this.allocator.handle(), memory, dstMemory);
    }

    public MappedMemory map() {
        return new MappedMemory(this, this.size);
    }

    public void unmap() {
        vmaUnmapMemory(this.allocator.handle(), memory);
    }

    public long handle() {
        return handle;
    }

    public long size() {
        return size;
    }

    public long memory() {
        return memory;
    }

    public long deviceAddress() {
        return deviceAddress;
    }

    public void info(VkDescriptorBufferInfo info) {
        info
                .offset(0)
                .buffer(this.handle())
                .range(this.size());
    }

    @Override
    public void close() {
        unmap();
        vmaDestroyBuffer(allocator.handle(), handle, memory);
        super.close();
    }
}
