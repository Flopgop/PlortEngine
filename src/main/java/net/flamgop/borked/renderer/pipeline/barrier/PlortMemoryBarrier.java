package net.flamgop.borked.renderer.pipeline.barrier;

import org.lwjgl.vulkan.VkMemoryBarrier;

public record PlortMemoryBarrier(int srcAccessMask, int dstAccessMask, long pNext) {
    public PlortMemoryBarrier(int srcAccessMask, int dstAccessMask) {
        this(srcAccessMask, dstAccessMask, 0);
    }

    public VkMemoryBarrier get(VkMemoryBarrier barrier) {
        return barrier
                .sType$Default()
                .srcAccessMask(this.srcAccessMask)
                .dstAccessMask(this.dstAccessMask)
                .pNext(this.pNext);
    }
}
