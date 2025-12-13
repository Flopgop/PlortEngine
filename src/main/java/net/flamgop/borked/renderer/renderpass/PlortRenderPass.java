package net.flamgop.borked.renderer.renderpass;

import net.flamgop.borked.renderer.PlortDevice;
import net.flamgop.borked.renderer.memory.TrackedCloseable;
import net.flamgop.borked.renderer.pipeline.PipelineBindPoint;
import net.flamgop.borked.renderer.pipeline.PipelineStage;
import net.flamgop.borked.renderer.util.VkUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.Collections;
import java.util.List;

import static org.lwjgl.vulkan.EXTDebugUtils.vkSetDebugUtilsObjectNameEXT;
import static org.lwjgl.vulkan.VK13.*;

public class PlortRenderPass extends TrackedCloseable {
    private final PlortDevice device;
    private final long renderPass;

    private final long[] framebuffers;
    private final int framesInFlight;

    private final List<PlortAttachment> attachments;

    private int width, height;

    @SuppressWarnings("resource")
    public PlortRenderPass(PlortDevice device, int framesInFlight, List<PlortAttachment> attachments, List<PlortAttachmentReference> colorReferences, PlortAttachmentReference depthReference) {
        super();
        this.device = device;
        this.attachments = Collections.unmodifiableList(attachments);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkAttachmentDescription.Buffer attachmentDescriptions = VkAttachmentDescription.calloc(attachments.size(), stack);

            for (int i = 0; i < attachments.size(); i++) {
                PlortAttachment attachment = attachments.get(i);
                attachmentDescriptions.get(i)
                        .format(attachment.format().qualifier())
                        .samples(attachment.sampleCount())
                        .loadOp(attachment.loadOp().qualifier())
                        .storeOp(attachment.storeOp().qualifier())
                        .stencilLoadOp(attachment.stencilLoadOp().qualifier())
                        .stencilStoreOp(attachment.stencilStoreOp().qualifier())
                        .initialLayout(attachment.initialLayout().qualifier())
                        .finalLayout(attachment.finalLayout().qualifier());
            }

            VkAttachmentReference.Buffer colorRefs = VkAttachmentReference.calloc(colorReferences.size(), stack);

            for (int i = 0; i < colorReferences.size(); i++) {
                PlortAttachmentReference reference = colorReferences.get(i);
                colorRefs.get(i)
                        .attachment(reference.attachment())
                        .layout(reference.layout().qualifier());
            }

            VkAttachmentReference depthRef = VkAttachmentReference.calloc(stack)
                    .attachment(depthReference.attachment())
                    .layout(depthReference.layout().qualifier());

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack)
                    .pipelineBindPoint(PipelineBindPoint.GRAPHICS.qualifier())
                    .colorAttachmentCount(colorReferences.size())
                    .pColorAttachments(colorRefs)
                    .pDepthStencilAttachment(depthRef);

            VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack)
                    .srcSubpass(VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(PipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT | PipelineStage.EARLY_FRAGMENT_TESTS_BIT)
                    .srcAccessMask(0)
                    .dstStageMask(PipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT | PipelineStage.EARLY_FRAGMENT_TESTS_BIT)
                    .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);

            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType$Default()
                    .pAttachments(attachmentDescriptions)
                    .pSubpasses(subpass)
                    .pDependencies(dependency);

            LongBuffer pRenderPass = stack.callocLong(1);
            VkUtil.check(vkCreateRenderPass(device.handle(), renderPassInfo, null, pRenderPass));
            this.renderPass = pRenderPass.get(0);
        }
        this.framebuffers = new long[framesInFlight];
        this.framesInFlight = framesInFlight;
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

    public void begin(VkCommandBuffer commandBuffer, VkClearValue.Buffer clearValues, int imageIndex) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(this.renderPass)
                    .framebuffer(this.framebuffer(imageIndex))
                    .renderArea(a -> a.offset(o -> o.set(0, 0)).extent(e -> e.set(width, height)))
                    .pClearValues(clearValues);

            vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
        }
    }

    public void end(VkCommandBuffer commandBuffer) {
        vkCmdEndRenderPass(commandBuffer);
    }

    public void recreate(int width, int height) {
        this.width = width;
        this.height = height;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pFramebuffer = stack.callocLong(1);
            for (int i = 0; i < framesInFlight; i++) {
                if (framebuffers[i] != VK_NULL_HANDLE) vkDestroyFramebuffer(device.handle(), framebuffers[i], null);

                LongBuffer attachments = stack.callocLong(this.attachments.size());
                for (int a = 0; a < this.attachments.size(); a++) {
                    PlortAttachment attachment = this.attachments.get(a);
                    attachments.put(a, attachment.imageViewSupplier().consume(width, height, i));
                }

                VkFramebufferCreateInfo fbInfo = VkFramebufferCreateInfo.calloc(stack)
                        .sType$Default()
                        .renderPass(renderPass)
                        .pAttachments(attachments)
                        .width(width)
                        .height(height)
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
        vkDestroyRenderPass(device.handle(), renderPass, null);
        super.close();
    }
}
