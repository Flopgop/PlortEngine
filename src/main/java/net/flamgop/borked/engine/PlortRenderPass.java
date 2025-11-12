package net.flamgop.borked.engine;

import net.flamgop.borked.engine.memory.PlortAllocator;
import net.flamgop.borked.engine.memory.TrackedCloseable;
import net.flamgop.borked.engine.swapchain.PlortSwapchain;
import net.flamgop.borked.engine.swapchain.SwapchainImage;
import net.flamgop.borked.engine.util.VkUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.EXTDebugUtils.vkSetDebugUtilsObjectNameEXT;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK13.*;

public class PlortRenderPass extends TrackedCloseable {
    private final PlortDevice device;
    private final PlortSwapchain swapchain;
    private final PlortAllocator allocator;
    private final long renderPass;
    private final long[] imageViews, depthImages, depthAllocations, depthImageViews, framebuffers;

    @SuppressWarnings("resource")
    public PlortRenderPass(PlortDevice device, PlortSwapchain swapchain, PlortAllocator allocator) {
        super();
        this.device = device;
        this.swapchain = swapchain;
        this.allocator = allocator;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkAttachmentDescription.Buffer attachmentDescriptions = VkAttachmentDescription.calloc(2, stack);

            attachmentDescriptions.get(0)
                    .format(swapchain.format())
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

            attachmentDescriptions.get(1)
                    .format(VK_FORMAT_D32_SFLOAT)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            VkAttachmentReference.Buffer colorRef = VkAttachmentReference.calloc(1, stack)
                    .attachment(0)
                    .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            VkAttachmentReference depthRef = VkAttachmentReference.calloc(stack)
                    .attachment(1)
                    .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack)
                    .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1)
                    .pColorAttachments(colorRef)
                    .pDepthStencilAttachment(depthRef);

            VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack)
                    .srcSubpass(VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
                    .srcAccessMask(0)
                    .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
                    .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);

            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType$Default()
                    .pAttachments(attachmentDescriptions)
                    .pSubpasses(subpass)
                    .pDependencies(dependency);

            LongBuffer pRenderPass = stack.callocLong(1);
            VkUtil.check(vkCreateRenderPass(device.handle(), renderPassInfo, null, pRenderPass));
            this.renderPass = pRenderPass.get(0);

            this.framebuffers = new long[swapchain.imageCount()];
            this.imageViews = new long[swapchain.imageCount()];
            this.depthImages = new long[swapchain.imageCount()];
            this.depthAllocations = new long[swapchain.imageCount()];
            this.depthImageViews = new long[swapchain.imageCount()];
            if (swapchain.extent().x() > 0 && swapchain.extent().y() > 0) {
                this.recreate();
            }
        }
    }

    public void label(String name) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDebugUtilsObjectNameInfoEXT nameInfo = VkDebugUtilsObjectNameInfoEXT.calloc(stack)
                    .sType$Default()
                    .objectType(VK_OBJECT_TYPE_RENDER_PASS)
                    .objectHandle(this.renderPass)
                    .pObjectName(stack.UTF8(name + " Render Pass"));

            vkSetDebugUtilsObjectNameEXT(this.device.handle(), nameInfo);
        }
    }

    public void recreate() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pFramebuffer = stack.callocLong(1);
            LongBuffer pImageView = stack.callocLong(1);
            LongBuffer pDepthImage = stack.callocLong(1);
            PointerBuffer pDepthAllocation = stack.callocPointer(1);
            LongBuffer pDepthImageView = stack.callocLong(1);

            VkImageCreateInfo depthImageInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(VK_FORMAT_D32_SFLOAT)
                    .extent(e -> e.set(swapchain.extent().x(), swapchain.extent().y(), 1))
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);

            VmaAllocationCreateInfo allocInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(VMA_MEMORY_USAGE_GPU_ONLY);

            VkImageViewCreateInfo depthViewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(VK_FORMAT_D32_SFLOAT)
                    .subresourceRange(sr -> sr
                            .aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT)
                            .baseMipLevel(0)
                            .levelCount(1)
                            .baseArrayLayer(0)
                            .layerCount(1));

            VkImageViewCreateInfo imageViewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(swapchain.format())
                    .components(c -> c
                            .r(VK_COMPONENT_SWIZZLE_IDENTITY)
                            .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                            .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                            .a(VK_COMPONENT_SWIZZLE_IDENTITY))
                    .subresourceRange(r -> r
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .baseMipLevel(0)
                            .levelCount(1)
                            .baseArrayLayer(0)
                            .layerCount(1));


            for (int i = 0; i < swapchain.imageCount(); i++) {
                if (framebuffers[i] != VK_NULL_HANDLE) vkDestroyFramebuffer(device.handle(), framebuffers[i], null);

                SwapchainImage image = swapchain.image(i);
                imageViewInfo.image(image.handle());
                VkUtil.check(vkCreateImageView(device.handle(), imageViewInfo, null, pImageView));
                if (imageViews[i] != VK_NULL_HANDLE) vkDestroyImageView(device.handle(), imageViews[i], null);
                imageViews[i] = pImageView.get(0);

                VkUtil.check(vmaCreateImage(allocator.handle(), depthImageInfo, allocInfo, pDepthImage, pDepthAllocation, null));
                long depthImage = pDepthImage.get(0);
                if (depthImages[i] != VK_NULL_HANDLE) vmaDestroyImage(allocator.handle(), depthImages[i], depthAllocations[i]);
                depthImages[i] = depthImage;
                depthAllocations[i] = pDepthAllocation.get(0);
                depthViewInfo.image(depthImage);
                VkUtil.check(vkCreateImageView(device.handle(), depthViewInfo, null, pDepthImageView));
                if (depthImageViews[i] != VK_NULL_HANDLE) vkDestroyImageView(device.handle(), depthImageViews[i], null);
                depthImageViews[i] = pDepthImageView.get(0);

                LongBuffer attachments = stack.longs(imageViews[i], depthImageViews[i]);

                VkFramebufferCreateInfo fbInfo = VkFramebufferCreateInfo.calloc(stack)
                        .sType$Default()
                        .renderPass(renderPass)
                        .pAttachments(attachments)
                        .width(swapchain.extent().x())
                        .height(swapchain.extent().y())
                        .layers(1);

                VkUtil.check(vkCreateFramebuffer(device.handle(), fbInfo, null, pFramebuffer));
                framebuffers[i] = pFramebuffer.get(0);
            }
        }
    }

    public long handle() {
        return this.renderPass;
    }

    public long framebuffer(int index) {
        return framebuffers[index];
    }

    @Override
    public void close() {
        for (long fb : framebuffers) vkDestroyFramebuffer(device.handle(), fb, null);
        for (long view : imageViews) vkDestroyImageView(device.handle(), view, null);
        for (long view : depthImageViews) vkDestroyImageView(device.handle(), view, null);
        for (int i = 0; i < depthImages.length; i++) vmaDestroyImage(allocator.handle(), depthImages[i], depthAllocations[i]);
        vkDestroyRenderPass(device.handle(), renderPass, null);
        super.close();
    }
}
