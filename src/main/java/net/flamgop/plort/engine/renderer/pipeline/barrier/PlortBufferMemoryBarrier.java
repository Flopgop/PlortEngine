package net.flamgop.plort.engine.renderer.pipeline.barrier;

import net.flamgop.plort.engine.renderer.memory.PlortBuffer;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;

public record PlortBufferMemoryBarrier(int srcAccessMask, int dstAccessMask, int srcQueueFamilyIndex, int dstQueueFamilyIndex, PlortBuffer buffer, long offset, long size, long pNext) {
    public PlortBufferMemoryBarrier(int srcAccessMask, int dstAccessMask, int srcQueueFamilyIndex, int dstQueueFamilyIndex, PlortBuffer buffer, long offset, long size) {
        this(srcAccessMask, dstAccessMask, srcQueueFamilyIndex, dstQueueFamilyIndex, buffer, offset, size, 0);
    }

    public VkBufferMemoryBarrier get(VkBufferMemoryBarrier barrier) {
        return barrier
                .sType$Default()
                .srcAccessMask(srcAccessMask)
                .dstAccessMask(dstAccessMask)
                .srcQueueFamilyIndex(srcQueueFamilyIndex)
                .dstQueueFamilyIndex(dstQueueFamilyIndex)
                .buffer(buffer.handle())
                .offset(offset)
                .size(size)
                .pNext(pNext);
    }
}
