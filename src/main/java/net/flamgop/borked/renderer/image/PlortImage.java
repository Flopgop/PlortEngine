package net.flamgop.borked.renderer.image;

import net.flamgop.borked.math.Vector3i;
import net.flamgop.borked.renderer.PlortCommandBuffer;
import net.flamgop.borked.renderer.memory.TrackedCloseable;
import net.flamgop.borked.renderer.memory.MemoryUsage;
import net.flamgop.borked.renderer.PlortDevice;
import net.flamgop.borked.renderer.memory.SharingMode;
import net.flamgop.borked.renderer.memory.PlortAllocator;
import net.flamgop.borked.renderer.util.VkUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.EXTDebugUtils.vkSetDebugUtilsObjectNameEXT;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK13.*;

public class PlortImage extends TrackedCloseable {
    public enum Type {
        TYPE_1D(VK_IMAGE_TYPE_1D),
        TYPE_2D(VK_IMAGE_TYPE_2D),
        TYPE_3D(VK_IMAGE_TYPE_3D),

        ;
        final int vkQualifier;
        Type(int vkQualifier) {
            this.vkQualifier = vkQualifier;
        }
        public int qualifier() {
            return vkQualifier;
        }
    }

    public enum ViewType {
        TYPE_1D(VK_IMAGE_VIEW_TYPE_1D),
        TYPE_2D(VK_IMAGE_VIEW_TYPE_2D),
        TYPE_3D(VK_IMAGE_VIEW_TYPE_3D),
        TYPE_CUBE(VK_IMAGE_VIEW_TYPE_CUBE),
        TYPE_1D_ARRAY(VK_IMAGE_VIEW_TYPE_1D_ARRAY),
        TYPE_2D_ARRAY(VK_IMAGE_VIEW_TYPE_2D_ARRAY),
        TYPE_CUBE_ARRAY(VK_IMAGE_VIEW_TYPE_CUBE_ARRAY),

        ;
        final int vkQualifier;
        ViewType(int vkQualifier) {
            this.vkQualifier = vkQualifier;
        }
        public int qualifier() {
            return vkQualifier;
        }
    }

    public enum Layout {
        UNDEFINED(VK_IMAGE_LAYOUT_UNDEFINED),
        GENERAL(VK_IMAGE_LAYOUT_GENERAL),
        COLOR_ATTACHMENT_OPTIMAL(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL),
        DEPTH_STENCIL_ATTACHMENT_OPTIMAL(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL),
        DEPTH_STENCIL_READ_ONLY_OPTIMAL(VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL),
        SHADER_READ_ONLY_OPTIMAL(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL),
        TRANSFER_SRC_OPTIMAL(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL),
        TRANSFER_DST_OPTIMAL(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL),
        PREINITIALIZED(VK_IMAGE_LAYOUT_PREINITIALIZED),
        ATTACHMENT_OPTIMAL(VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL),

        PRESENT_SRC_KHR(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)

        ;
        final int vkQualifier;
        Layout(int vkQualifier) {
            this.vkQualifier = vkQualifier;
        }
        public int qualifier() {
            return vkQualifier;
        }

        public static Layout valueOf(int value) {
            return switch (value) {
                case VK_IMAGE_LAYOUT_UNDEFINED -> UNDEFINED;
                case VK_IMAGE_LAYOUT_GENERAL -> GENERAL;
                case VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL -> COLOR_ATTACHMENT_OPTIMAL;
                case VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL -> DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
                case VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL -> DEPTH_STENCIL_READ_ONLY_OPTIMAL;
                case VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL -> SHADER_READ_ONLY_OPTIMAL;
                case VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL -> TRANSFER_SRC_OPTIMAL;
                case VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> TRANSFER_DST_OPTIMAL;
                case VK_IMAGE_LAYOUT_PREINITIALIZED -> PREINITIALIZED;
                case VK_IMAGE_LAYOUT_PRESENT_SRC_KHR -> PRESENT_SRC_KHR;
                default -> throw new IndexOutOfBoundsException("Unknown image layout value.");
            };
        }
    }

    private final PlortDevice device;
    private final PlortAllocator allocator;
    private final long image, allocation;
    private final long view;

    private final int mipLevels, arrayLayers;
    private final int aspectMask;

    public static long createView(PlortDevice device, long image, ViewType viewType, ImageFormat format, int aspectMask, int mipLevels, int arrayLayers) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageViewCreateInfo imageViewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .image(image)
                    .viewType(viewType.qualifier())
                    .format(format.qualifier())
                    .components(c -> c
                            .r(VK_COMPONENT_SWIZZLE_IDENTITY)
                            .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                            .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                            .a(VK_COMPONENT_SWIZZLE_IDENTITY))
                    .subresourceRange(r -> r
                            .aspectMask(aspectMask)
                            .baseMipLevel(0)
                            .levelCount(mipLevels)
                            .baseArrayLayer(0)
                            .layerCount(arrayLayers));

            LongBuffer pImageView = stack.callocLong(1);
            VkUtil.check(vkCreateImageView(device.handle(), imageViewInfo, null, pImageView));
            return pImageView.get(0);
        }
    }

    public static void destroyView(PlortDevice device, long view) {
        vkDestroyImageView(device.handle(), view, null);
    }

    public PlortImage(
            PlortDevice device,
            PlortAllocator allocator,
            Type type,
            Vector3i extent,
            int mipLevels, int arrayLayers,
            ImageFormat format,
            Layout initialLayout,
            int usage, int sampleCount,
            SharingMode sharingMode,
            MemoryUsage memoryUsage,

            ViewType viewType,
            int aspectMask
    ) {
        super();
        this.device = device;
        this.allocator = allocator;
        this.mipLevels = mipLevels; this.arrayLayers = arrayLayers;
        this.aspectMask = aspectMask;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .imageType(type.qualifier())
                    .extent(e -> e.set(extent.x(), extent.y(), extent.z()))
                    .mipLevels(mipLevels)
                    .arrayLayers(arrayLayers)
                    .format(format.qualifier())
                    .initialLayout(initialLayout.qualifier())
                    .usage(usage)
                    .samples(sampleCount)
                    .sharingMode(sharingMode.qualifier());

            VmaAllocationCreateInfo allocInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(memoryUsage.qualifier());

            LongBuffer pImage = stack.callocLong(1);
            PointerBuffer pAlloc = stack.callocPointer(1);
            VkUtil.check(vmaCreateImage(allocator.handle(), imageInfo, allocInfo, pImage, pAlloc, null));
            this.image = pImage.get(0); this.allocation = pAlloc.get(0);

            VkImageViewCreateInfo imageViewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .image(image)
                    .viewType(viewType.qualifier())
                    .format(format.qualifier())
                    .components(c -> c
                            .r(VK_COMPONENT_SWIZZLE_IDENTITY)
                            .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                            .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                            .a(VK_COMPONENT_SWIZZLE_IDENTITY))
                    .subresourceRange(r -> r
                            .aspectMask(aspectMask)
                            .baseMipLevel(0)
                            .levelCount(mipLevels)
                            .baseArrayLayer(0)
                            .layerCount(arrayLayers));

            LongBuffer pImageView = stack.callocLong(1);
            VkUtil.check(vkCreateImageView(device.handle(), imageViewInfo, null, pImageView));
            this.view = pImageView.get(0);
        }
    }

    public void transitionLayout(
            PlortCommandBuffer commandBuffer,
            Layout oldLayout,
            Layout newLayout,
            int srcStage,
            int dstStage,
            int srcAccessMask,
            int dstAccessMask
    ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            this.transitionLayout(barrier.get(0), oldLayout, newLayout, srcAccessMask, dstAccessMask);

            commandBuffer.pipelineBarrier(srcStage, dstStage, 0, null, null, barrier);
        }
    }

    public void transitionLayout(
        VkImageMemoryBarrier barrier,
        Layout oldLayout,
        Layout newLayout,
        int srcAccessMask,
        int dstAccessMask
    ) {
        barrier
            .sType$Default()
            .oldLayout(oldLayout.qualifier())
            .newLayout(newLayout.qualifier())
            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .image(image)
            .subresourceRange(r -> r
                    .aspectMask(aspectMask)
                    .baseMipLevel(0)
                    .levelCount(mipLevels)
                    .baseArrayLayer(0)
                    .layerCount(arrayLayers))
            .srcAccessMask(srcAccessMask)
            .dstAccessMask(dstAccessMask);
    }

    public void label(String name) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDebugUtilsObjectNameInfoEXT nameInfo = VkDebugUtilsObjectNameInfoEXT.calloc(stack)
                    .sType$Default()
                    .objectType(VK_OBJECT_TYPE_IMAGE)
                    .objectHandle(this.image)
                    .pObjectName(stack.UTF8(name + " Image"));

            vkSetDebugUtilsObjectNameEXT(this.device.handle(), nameInfo);

            VkDebugUtilsObjectNameInfoEXT nameInfo1 = VkDebugUtilsObjectNameInfoEXT.calloc(stack)
                    .sType$Default()
                    .objectType(VK_OBJECT_TYPE_IMAGE_VIEW)
                    .objectHandle(this.view)
                    .pObjectName(stack.UTF8(name + " Image View"));

            vkSetDebugUtilsObjectNameEXT(this.device.handle(), nameInfo1);

        }
    }

    public long handle() {
        return image;
    }

    public long view() {
        return view;
    }

    public void info(VkDescriptorImageInfo info) {
        info.imageView(this.view());
    }

    @Override
    public void close() {
        vkDestroyImageView(device.handle(), view, null);
        vmaDestroyImage(allocator.handle(), image, allocation);
        super.close();
    }
}
