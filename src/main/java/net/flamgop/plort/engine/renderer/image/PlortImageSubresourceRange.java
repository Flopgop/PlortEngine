package net.flamgop.plort.engine.renderer.image;

import org.lwjgl.vulkan.VkImageSubresourceRange;

public record PlortImageSubresourceRange(int aspectMask, int baseMipLevel, int levelCount, int baseArrayLevel, int layerCount) {
    public VkImageSubresourceRange get(VkImageSubresourceRange range) {
        return range
                .aspectMask(aspectMask)
                .baseMipLevel(baseMipLevel)
                .levelCount(levelCount)
                .baseArrayLayer(baseArrayLevel)
                .layerCount(layerCount);
    }
}
