package net.flamgop.borked.renderer.pipeline.barrier;

import net.flamgop.borked.renderer.image.PlortImage;
import net.flamgop.borked.renderer.image.PlortImageSubresourceRange;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceRange;

public record PlortImageMemoryBarrier(int srcAccessMask, int dstAccessMask, PlortImage.Layout oldLayout, PlortImage.Layout newLayout, int srcQueueFamilyIndex, int dstQueueFamilyIndex, PlortImage image, PlortImageSubresourceRange subresourceRange, long pNext) {
    public PlortImageMemoryBarrier(int srcAccessMask, int dstAccessMask, PlortImage.Layout oldLayout, PlortImage.Layout newLayout, int srcQueueFamilyIndex, int dstQueueFamilyIndex, PlortImage image, PlortImageSubresourceRange subresourceRange) {
        this(srcAccessMask, dstAccessMask, oldLayout, newLayout, srcQueueFamilyIndex, dstQueueFamilyIndex, image, subresourceRange, 0);
    }

    public VkImageMemoryBarrier get(MemoryStack stack, VkImageMemoryBarrier barrier) {
        return barrier
                .sType$Default()
                .srcAccessMask(srcAccessMask)
                .dstAccessMask(dstAccessMask)
                .oldLayout(oldLayout.qualifier())
                .newLayout(newLayout.qualifier())
                .srcQueueFamilyIndex(srcQueueFamilyIndex)
                .dstQueueFamilyIndex(dstQueueFamilyIndex)
                .image(image.handle())
                .subresourceRange(subresourceRange.get(VkImageSubresourceRange.calloc(stack)));
    }
}
