package net.flamgop.borked;

import net.flamgop.borked.renderer.descriptor.PlortBufferedDescriptorSetPool;
import net.flamgop.borked.renderer.PlortEngine;
import net.flamgop.borked.renderer.descriptor.PlortDescriptor;
import net.flamgop.borked.renderer.descriptor.PlortDescriptorSetLayout;
import net.flamgop.borked.renderer.image.*;
import net.flamgop.borked.renderer.material.PlortTexture;
import net.flamgop.borked.renderer.memory.MemoryUsage;
import net.flamgop.borked.renderer.memory.SharingMode;
import net.flamgop.borked.renderer.pipeline.*;
import net.flamgop.borked.renderer.renderpass.*;
import net.flamgop.borked.renderer.util.ResourceHelper;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.util.List;

import static org.lwjgl.vulkan.EXTMeshShader.vkCmdDrawMeshTasksEXT;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;

public class GBuffer implements AutoCloseable {
    private final PlortSampler gbufferSampler;
    private final PlortShaderModule gbufferModule;
    private final PlortDescriptorSetLayout gbufferLayout;
    private final PlortBufferedDescriptorSetPool gbufferDescriptors;
    private final PlortPipeline gbufferPipeline;

    private final PlortImage[] gPositionImages;
    private final PlortImage[] gNormalImages;
    private final PlortImage[] gAlbedoImages;
    private final PlortImage[] gDepthImages;
    private final PlortRenderPass gbufferRenderPass;

    public GBuffer(PlortEngine engine, PlortRenderPass mainRenderPass) {
        gPositionImages = new PlortImage[engine.swapchain().imageCount()];
        gNormalImages = new PlortImage[engine.swapchain().imageCount()];
        gAlbedoImages = new PlortImage[engine.swapchain().imageCount()];
        gDepthImages = new PlortImage[engine.swapchain().imageCount()];

        this.gbufferRenderPass = new PlortRenderPass(
                engine.device(),
                engine.swapchain().imageCount(),
                List.of(
                        new PlortAttachment(
                                ImageFormat.R16G16B16A16_SFLOAT,
                                VK_SAMPLE_COUNT_1_BIT,
                                AttachmentLoadOp.CLEAR,
                                AttachmentStoreOp.STORE,
                                AttachmentLoadOp.DONT_CARE,
                                AttachmentStoreOp.DONT_CARE,
                                PlortImage.Layout.UNDEFINED,
                                PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL,
                                (w, h, f) -> {
                                    if (gPositionImages[f] != null) gPositionImages[f].close();
                                    gPositionImages[f] = new PlortImage(
                                            engine.device(),
                                            engine.allocator(),
                                            PlortImage.Type.TYPE_2D,
                                            new Vector3i(w, h, 1),
                                            1, 1,
                                            ImageFormat.R16G16B16A16_SFLOAT,
                                            PlortImage.Layout.UNDEFINED,
                                            ImageUsage.COLOR_ATTACHMENT_BIT | ImageUsage.SAMPLED_BIT,
                                            VK_SAMPLE_COUNT_1_BIT,
                                            SharingMode.EXCLUSIVE,
                                            MemoryUsage.GPU_ONLY,
                                            PlortImage.ViewType.TYPE_2D,
                                            AspectMask.COLOR_BIT
                                    );
                                    return gPositionImages[f].view();
                                }
                        ),
                        new PlortAttachment(
                                ImageFormat.R16G16B16A16_SFLOAT,
                                VK_SAMPLE_COUNT_1_BIT,
                                AttachmentLoadOp.CLEAR,
                                AttachmentStoreOp.STORE,
                                AttachmentLoadOp.DONT_CARE,
                                AttachmentStoreOp.DONT_CARE,
                                PlortImage.Layout.UNDEFINED,
                                PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL,
                                (w, h, f) -> {
                                    if (gNormalImages[f] != null) gNormalImages[f].close();
                                    gNormalImages[f] = new PlortImage(
                                            engine.device(),
                                            engine.allocator(),
                                            PlortImage.Type.TYPE_2D,
                                            new Vector3i(w, h, 1),
                                            1, 1,
                                            ImageFormat.R16G16B16A16_SFLOAT,
                                            PlortImage.Layout.UNDEFINED,
                                            ImageUsage.COLOR_ATTACHMENT_BIT | ImageUsage.SAMPLED_BIT,
                                            VK_SAMPLE_COUNT_1_BIT,
                                            SharingMode.EXCLUSIVE,
                                            MemoryUsage.GPU_ONLY,
                                            PlortImage.ViewType.TYPE_2D,
                                            AspectMask.COLOR_BIT
                                    );
                                    return gNormalImages[f].view();
                                }
                        ),
                        new PlortAttachment(
                                ImageFormat.R8G8B8A8_UNORM,
                                VK_SAMPLE_COUNT_1_BIT,
                                AttachmentLoadOp.CLEAR,
                                AttachmentStoreOp.STORE,
                                AttachmentLoadOp.DONT_CARE,
                                AttachmentStoreOp.DONT_CARE,
                                PlortImage.Layout.UNDEFINED,
                                PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL,
                                (w, h, f) -> {
                                    if (gAlbedoImages[f] != null) gAlbedoImages[f].close();
                                    gAlbedoImages[f] = new PlortImage(
                                            engine.device(),
                                            engine.allocator(),
                                            PlortImage.Type.TYPE_2D,
                                            new Vector3i(w, h, 1),
                                            1, 1,
                                            ImageFormat.R8G8B8A8_UNORM,
                                            PlortImage.Layout.UNDEFINED,
                                            ImageUsage.COLOR_ATTACHMENT_BIT | ImageUsage.SAMPLED_BIT,
                                            VK_SAMPLE_COUNT_1_BIT,
                                            SharingMode.EXCLUSIVE,
                                            MemoryUsage.GPU_ONLY,
                                            PlortImage.ViewType.TYPE_2D,
                                            AspectMask.COLOR_BIT
                                    );
                                    return gAlbedoImages[f].view();
                                }
                        ),
                        new PlortAttachment(
                                ImageFormat.D32_SFLOAT,
                                VK_SAMPLE_COUNT_1_BIT,
                                AttachmentLoadOp.CLEAR,
                                AttachmentStoreOp.DONT_CARE,
                                AttachmentLoadOp.DONT_CARE,
                                AttachmentStoreOp.DONT_CARE,
                                PlortImage.Layout.UNDEFINED,
                                PlortImage.Layout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                                (w, h, f) -> {
                                    if (gDepthImages[f] != null) gDepthImages[f].close();
                                    gDepthImages[f] = new PlortImage(
                                            engine.device(), engine.allocator(),
                                            PlortImage.Type.TYPE_2D, new Vector3i(w, h, 1),
                                            1, 1, ImageFormat.D32_SFLOAT,
                                            PlortImage.Layout.UNDEFINED,
                                            ImageUsage.DEPTH_STENCIL_ATTACHMENT_BIT | ImageUsage.SAMPLED_BIT,
                                            VK_SAMPLE_COUNT_1_BIT,
                                            SharingMode.EXCLUSIVE,
                                            MemoryUsage.GPU_ONLY,
                                            PlortImage.ViewType.TYPE_2D,
                                            AspectMask.DEPTH_BIT
                                    );

                                    return gDepthImages[f].view();
                                }
                        )
                ),
                List.of(
                        new PlortAttachmentReference(0, PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL),
                        new PlortAttachmentReference(1, PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL),
                        new PlortAttachmentReference(2, PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL)
                ),
                new PlortAttachmentReference(3, PlortImage.Layout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
        );
        gbufferRenderPass.recreate(engine.swapchain().extent().x(), engine.swapchain().extent().y());
        gbufferRenderPass.label("G-Buffer");

        this.gbufferSampler = new PlortSampler(engine.device(), PlortSampler.Filter.NEAREST, PlortSampler.Filter.NEAREST, PlortSampler.AddressMode.CLAMP_TO_EDGE, PlortSampler.AddressMode.CLAMP_TO_EDGE, PlortSampler.AddressMode.CLAMP_TO_EDGE);

        ByteBuffer gbufferCode = ResourceHelper.loadFromResource("assets/shaders/gbuffer.spv");
        this.gbufferModule = new PlortShaderModule(engine.device(), gbufferCode);
        gbufferModule.label("G-Buffer");
        MemoryUtil.memFree(gbufferCode);

        this.gbufferLayout = new PlortDescriptorSetLayout(
                engine.device(),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.UNIFORM_BUFFER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.UNIFORM_BUFFER, 1, PlortShaderStage.Stage.FRAGMENT.bit())
        );
        this.gbufferDescriptors = new PlortBufferedDescriptorSetPool(engine.device(), gbufferLayout, 1, engine.swapchain().imageCount());

        this.gbufferPipeline = PlortPipeline.builder(engine.device(), mainRenderPass)
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.MESH, gbufferModule, "meshMain"))
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.FRAGMENT, gbufferModule, "fragmentMain"))
                .descriptorSetLayouts(gbufferLayout)
                .blendState(PlortBlendState.disabled())
                .buildGraphics();
    }

    public PlortRenderPass renderPass() {
        return this.gbufferRenderPass;
    }

    public PlortImage position(int imageIndex) {
        return gPositionImages[imageIndex];
    }

    public PlortImage normal(int imageIndex) {
        return gNormalImages[imageIndex];
    }

    public PlortImage albedo(int imageIndex) {
        return gAlbedoImages[imageIndex];
    }

    public PlortImage depth(int imageIndex) {
        return gDepthImages[imageIndex];
    }

    public PlortTexture positionTexture(int imageIndex) {
        return new PlortTexture(position(imageIndex), gbufferSampler);
    }

    public PlortTexture normalTexture(int imageIndex) {
        return new PlortTexture(normal(imageIndex), gbufferSampler);
    }

    public PlortTexture albedoTexture(int imageIndex) {
        return new PlortTexture(albedo(imageIndex), gbufferSampler);
    }

    public PlortTexture depthTexture(int imageIndex) {
        return new PlortTexture(depth(imageIndex), gbufferSampler);
    }

    public PlortSampler sampler() {
        return this.gbufferSampler;
    }

    public PlortBufferedDescriptorSetPool descriptors() {
        return this.gbufferDescriptors;
    }

    public void submitShadingPass(VkCommandBuffer cmdBuffer, int currentFrameModInFlight) {
        try (MemoryStack stack =  MemoryStack.stackPush()) {
            gbufferPipeline.bind(cmdBuffer, PipelineBindPoint.GRAPHICS);
            long gbufferDescriptor = gbufferDescriptors.descriptorSet(currentFrameModInFlight, 0);

            vkCmdBindDescriptorSets(cmdBuffer, PipelineBindPoint.GRAPHICS.qualifier(), gbufferPipeline.layout(), 0, stack.longs(gbufferDescriptor), null);

            vkCmdDrawMeshTasksEXT(cmdBuffer, 1, 1, 1);
        }
    }

    public void transitionImagesForSubmit(VkCommandBuffer cmdBuffer, int imageIndex) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barriers = VkImageMemoryBarrier.calloc(4, stack);
            gPositionImages[imageIndex].transitionLayout(
                    barriers.get(0),
                    PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL,
                    PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL,
                    VK_ACCESS_SHADER_READ_BIT,
                    VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
            );
            gNormalImages[imageIndex].transitionLayout(
                    barriers.get(1),
                    PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL,
                    PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL,
                    VK_ACCESS_SHADER_READ_BIT,
                    VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
            );
            gAlbedoImages[imageIndex].transitionLayout(
                    barriers.get(2),
                    PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL,
                    PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL,
                    VK_ACCESS_SHADER_READ_BIT,
                    VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
            );
            gDepthImages[imageIndex].transitionLayout(
                    barriers.get(3),
                    PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL,
                    PlortImage.Layout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                    VK_ACCESS_SHADER_READ_BIT,
                    VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
            );

            vkCmdPipelineBarrier(cmdBuffer, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, 0, null, null, barriers.slice(0, 3));
            vkCmdPipelineBarrier(cmdBuffer, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT, 0, null, null, barriers.slice(3, 1));
        }
    }

    public void transitionImagesForShading(VkCommandBuffer cmdBuffer, int imageIndex) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barriers = VkImageMemoryBarrier.calloc(4, stack);
            gPositionImages[imageIndex].transitionLayout(
                    barriers.get(0),
                    PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL,
                    PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL,
                    VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_ACCESS_SHADER_READ_BIT
            );
            gNormalImages[imageIndex].transitionLayout(
                    barriers.get(1),
                    PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL,
                    PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL,
                    VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_ACCESS_SHADER_READ_BIT
            );
            gAlbedoImages[imageIndex].transitionLayout(
                    barriers.get(2),
                    PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL,
                    PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL,
                    VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_ACCESS_SHADER_READ_BIT
            );
            gDepthImages[imageIndex].transitionLayout(
                    barriers.get(3),
                    PlortImage.Layout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                    PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL,
                    VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                    VK_ACCESS_SHADER_READ_BIT
            );

            vkCmdPipelineBarrier(cmdBuffer, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, null, null, barriers.slice(0, 3));
            vkCmdPipelineBarrier(cmdBuffer, VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, null, null, barriers.slice(3, 1));
        }
    }

    public void beginSubmitPass(VkCommandBuffer cmdBuffer, VkClearValue.Buffer clearValues, int imageIndex) {
        gbufferRenderPass.begin(cmdBuffer, clearValues, imageIndex);
    }

    public void endSubmitPass(VkCommandBuffer cmdBuffer) {
        gbufferRenderPass.end(cmdBuffer);
    }

    public void recreate(int width, int height) {
        this.gbufferRenderPass.recreate(width, height);
    }

    @Override
    public void close() {
        gbufferPipeline.close();
        gbufferDescriptors.close();
        gbufferLayout.close();
        gbufferModule.close();
        gbufferSampler.close();
        gbufferRenderPass.close();
        for (PlortImage posImage : gPositionImages) if (posImage != null) posImage.close();
        for (PlortImage normImage : gNormalImages) if (normImage != null) normImage.close();
        for (PlortImage albImage : gAlbedoImages) if (albImage != null) albImage.close();
        for (PlortImage depthImage : gDepthImages) if (depthImage != null) depthImage.close();
    }
}
