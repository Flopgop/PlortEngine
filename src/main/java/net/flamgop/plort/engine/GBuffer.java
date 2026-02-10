package net.flamgop.plort.engine;

import net.flamgop.plort.engine.math.val.Vector3i;
import net.flamgop.plort.engine.renderer.PlortCommandBuffer;
import net.flamgop.plort.engine.renderer.PlortRenderContext;
import net.flamgop.plort.engine.renderer.descriptor.PlortBufferedDescriptorSetPool;
import net.flamgop.plort.engine.renderer.descriptor.PlortDescriptor;
import net.flamgop.plort.engine.renderer.descriptor.PlortDescriptorSetLayout;
import net.flamgop.plort.engine.renderer.image.*;
import net.flamgop.plort.engine.renderer.image.*;
import net.flamgop.plort.engine.renderer.material.PlortTexture;
import net.flamgop.plort.engine.renderer.memory.MemoryUsage;
import net.flamgop.plort.engine.renderer.memory.SharingMode;
import net.flamgop.plort.engine.renderer.pipeline.*;
import net.flamgop.plort.engine.renderer.pipeline.*;
import net.flamgop.plort.engine.renderer.pipeline.barrier.PlortImageMemoryBarrier;
import net.flamgop.plort.engine.renderer.renderpass.*;
import net.flamgop.plort.engine.renderer.renderpass.*;
import net.flamgop.plort.engine.resource.ResourceIdentifier;
import net.flamgop.plort.engine.resource.ResourceManager;
import net.flamgop.plort.engine.util.ShaderHelper;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.List;

import static org.lwjgl.vulkan.VK10.*;

public class GBuffer implements AutoCloseable {
    private final PlortSampler gbufferSampler;
    private final PlortShaderModule gbufferModule;
    private final PlortDescriptorSetLayout gbufferLayout;
    private final PlortBufferedDescriptorSetPool gbufferDescriptors;
    private final PlortPipelineLayout gbufferPipelineLayout;
    private final PlortPipeline gbufferPipeline;

    private final PlortImage[] gPositionImages;
    private final PlortImage[] gNormalImages;
    private final PlortImage[] gAlbedoImages;
    private final PlortImage[] gDepthImages;
    private final PlortRenderPass gbufferRenderPass;

    public GBuffer(ResourceManager resourceManager, PlortRenderContext engine, PlortRenderPass mainRenderPass) {
        gPositionImages = new PlortImage[engine.swapchain().imageCount()];
        gNormalImages = new PlortImage[engine.swapchain().imageCount()];
        gAlbedoImages = new PlortImage[engine.swapchain().imageCount()];
        gDepthImages = new PlortImage[engine.swapchain().imageCount()];

        this.gbufferRenderPass = new PlortRenderPass(
                engine.device(),
                engine.swapchain().imageCount(),
                List.of(
                        new PlortAttachment(
                                ImageFormat.R16G16B16A16_SFLOAT, VK_SAMPLE_COUNT_1_BIT,
                                AttachmentLoadOp.CLEAR, AttachmentStoreOp.STORE,
                                AttachmentLoadOp.DONT_CARE, AttachmentStoreOp.DONT_CARE,
                                PlortImage.Layout.UNDEFINED, PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL,
                                (w, h, f) -> {
                                    if (gPositionImages[f] != null) gPositionImages[f].close();
                                    gPositionImages[f] = new PlortImage(
                                            engine.device(), engine.allocator(),
                                            PlortImage.Type.TYPE_2D, new Vector3i(w, h, 1),
                                            1, 1, ImageFormat.R16G16B16A16_SFLOAT, PlortImage.Layout.UNDEFINED,
                                            ImageUsage.COLOR_ATTACHMENT_BIT | ImageUsage.SAMPLED_BIT, VK_SAMPLE_COUNT_1_BIT, SharingMode.EXCLUSIVE, MemoryUsage.GPU_ONLY, PlortImage.ViewType.TYPE_2D, AspectMask.COLOR_BIT
                                    );
                                    return gPositionImages[f].view();
                                }
                        ),
                        new PlortAttachment(
                                ImageFormat.R16G16B16A16_SFLOAT, VK_SAMPLE_COUNT_1_BIT,
                                AttachmentLoadOp.CLEAR, AttachmentStoreOp.STORE,
                                AttachmentLoadOp.DONT_CARE, AttachmentStoreOp.DONT_CARE,
                                PlortImage.Layout.UNDEFINED, PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL,
                                (w, h, f) -> {
                                    if (gNormalImages[f] != null) gNormalImages[f].close();
                                    gNormalImages[f] = new PlortImage(
                                            engine.device(), engine.allocator(),
                                            PlortImage.Type.TYPE_2D, new Vector3i(w, h, 1),
                                            1, 1, ImageFormat.R16G16B16A16_SFLOAT, PlortImage.Layout.UNDEFINED,
                                            ImageUsage.COLOR_ATTACHMENT_BIT | ImageUsage.SAMPLED_BIT, VK_SAMPLE_COUNT_1_BIT,
                                            SharingMode.EXCLUSIVE, MemoryUsage.GPU_ONLY, PlortImage.ViewType.TYPE_2D, AspectMask.COLOR_BIT
                                    );
                                    return gNormalImages[f].view();
                                }
                        ),
                        new PlortAttachment(
                                ImageFormat.R8G8B8A8_UNORM, VK_SAMPLE_COUNT_1_BIT,
                                AttachmentLoadOp.CLEAR, AttachmentStoreOp.STORE,
                                AttachmentLoadOp.DONT_CARE, AttachmentStoreOp.DONT_CARE,
                                PlortImage.Layout.UNDEFINED, PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL,
                                (w, h, f) -> {
                                    if (gAlbedoImages[f] != null) gAlbedoImages[f].close();
                                    gAlbedoImages[f] = new PlortImage(
                                            engine.device(), engine.allocator(),
                                            PlortImage.Type.TYPE_2D, new Vector3i(w, h, 1),
                                            1, 1, ImageFormat.R8G8B8A8_UNORM, PlortImage.Layout.UNDEFINED,
                                            ImageUsage.COLOR_ATTACHMENT_BIT | ImageUsage.SAMPLED_BIT, VK_SAMPLE_COUNT_1_BIT,
                                            SharingMode.EXCLUSIVE, MemoryUsage.GPU_ONLY, PlortImage.ViewType.TYPE_2D, AspectMask.COLOR_BIT
                                    );
                                    return gAlbedoImages[f].view();
                                }
                        ),
                        new PlortAttachment(
                                ImageFormat.D32_SFLOAT, VK_SAMPLE_COUNT_1_BIT,
                                AttachmentLoadOp.CLEAR, AttachmentStoreOp.DONT_CARE,
                                AttachmentLoadOp.DONT_CARE, AttachmentStoreOp.DONT_CARE,
                                PlortImage.Layout.UNDEFINED, PlortImage.Layout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                                (w, h, f) -> {
                                    if (gDepthImages[f] != null) gDepthImages[f].close();
                                    gDepthImages[f] = new PlortImage(
                                            engine.device(), engine.allocator(),
                                            PlortImage.Type.TYPE_2D, new Vector3i(w, h, 1),
                                            1, 1, ImageFormat.D32_SFLOAT, PlortImage.Layout.UNDEFINED,
                                            ImageUsage.DEPTH_STENCIL_ATTACHMENT_BIT | ImageUsage.SAMPLED_BIT, VK_SAMPLE_COUNT_1_BIT,
                                            SharingMode.EXCLUSIVE, MemoryUsage.GPU_ONLY, PlortImage.ViewType.TYPE_2D, AspectMask.DEPTH_BIT
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

        this.gbufferSampler = new PlortSampler(engine.device(), PlortFilter.NEAREST, PlortFilter.NEAREST, PlortSampler.AddressMode.CLAMP_TO_EDGE, PlortSampler.AddressMode.CLAMP_TO_EDGE, PlortSampler.AddressMode.CLAMP_TO_EDGE);

        this.gbufferModule = ShaderHelper.load(engine.device(), resourceManager, ResourceIdentifier.withDefaultNamespace("shaders/gbuffer/gbuffer.spv"));
        gbufferModule.label("G-Buffer");

        this.gbufferLayout = new PlortDescriptorSetLayout(
                engine.device(),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.UNIFORM_BUFFER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.UNIFORM_BUFFER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.UNIFORM_BUFFER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.UNIFORM_BUFFER, 1, PlortShaderStage.Stage.FRAGMENT.bit())
        );
        this.gbufferDescriptors = new PlortBufferedDescriptorSetPool(engine.device(), gbufferLayout, 1, engine.swapchain().imageCount());

        this.gbufferPipelineLayout = PlortPipelineLayout.builder(engine.device())
                .descriptorSetLayouts(gbufferLayout)
                .build();
        this.gbufferPipeline = PlortPipeline.builder(engine.device(), mainRenderPass)
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.MESH, gbufferModule, "meshMain"))
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.FRAGMENT, gbufferModule, "fragmentMain"))
                .layout(gbufferPipelineLayout)
                .blendState(PlortBlendState.disabled())
                .buildGraphics();
    }

    public PlortPipelineLayout pipelineLayout() {
        return gbufferPipelineLayout;
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

    public void bindDescriptorSet(PlortCommandBuffer cmdBuffer, int frame) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long gbufferDescriptor = gbufferDescriptors.descriptorSet(frame, 0);
            cmdBuffer.bindDescriptorSets(PipelineBindPoint.GRAPHICS, gbufferPipelineLayout, 0, stack.longs(gbufferDescriptor), null);
        }
    }

    public void submitShadingPass(PlortCommandBuffer cmdBuffer) {
        gbufferPipeline.bind(cmdBuffer, PipelineBindPoint.GRAPHICS);
        cmdBuffer.drawMeshTasksEXT(1,1,1);
    }

    public void transitionImagesForSubmit(PlortCommandBuffer cmdBuffer, int imageIndex) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PlortImageMemoryBarrier[] color = new PlortImageMemoryBarrier[]{
                    new PlortImageMemoryBarrier(
                            VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                            PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL,
                            VK_QUEUE_FAMILY_IGNORED, VK_QUEUE_FAMILY_IGNORED,
                            gPositionImages[imageIndex], gPositionImages[imageIndex].entireResourceRange()
                    ),
                    new PlortImageMemoryBarrier(
                            VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                            PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL,
                            VK_QUEUE_FAMILY_IGNORED, VK_QUEUE_FAMILY_IGNORED,
                            gNormalImages[imageIndex], gNormalImages[imageIndex].entireResourceRange()
                    ),
                    new PlortImageMemoryBarrier(
                            VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                            PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL,
                            VK_QUEUE_FAMILY_IGNORED, VK_QUEUE_FAMILY_IGNORED,
                            gAlbedoImages[imageIndex], gAlbedoImages[imageIndex].entireResourceRange()
                    )
            };
            PlortImageMemoryBarrier[] depth = new PlortImageMemoryBarrier[]{
                    new PlortImageMemoryBarrier(
                            VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                            PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, PlortImage.Layout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                            VK_QUEUE_FAMILY_IGNORED, VK_QUEUE_FAMILY_IGNORED,
                            gDepthImages[imageIndex], gDepthImages[imageIndex].entireResourceRange()
                    )
            };

            cmdBuffer.pipelineBarrier(stack, PipelineStage.FRAGMENT_SHADER_BIT, PipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT, 0, null, null, color);
            cmdBuffer.pipelineBarrier(stack, PipelineStage.FRAGMENT_SHADER_BIT, PipelineStage.LATE_FRAGMENT_TESTS_BIT, 0, null, null, depth);
        }
    }

    public void transitionImagesForShading(PlortCommandBuffer cmdBuffer, int imageIndex) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PlortImageMemoryBarrier[] color = new PlortImageMemoryBarrier[]{
                    new PlortImageMemoryBarrier(
                            VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT,
                            PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL,
                            VK_QUEUE_FAMILY_IGNORED, VK_QUEUE_FAMILY_IGNORED,
                            gPositionImages[imageIndex], gPositionImages[imageIndex].entireResourceRange()
                    ),
                    new PlortImageMemoryBarrier(
                            VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT,
                            PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL,
                            VK_QUEUE_FAMILY_IGNORED, VK_QUEUE_FAMILY_IGNORED,
                            gNormalImages[imageIndex], gNormalImages[imageIndex].entireResourceRange()
                    ),
                    new PlortImageMemoryBarrier(
                            VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT,
                            PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL,
                            VK_QUEUE_FAMILY_IGNORED, VK_QUEUE_FAMILY_IGNORED,
                            gAlbedoImages[imageIndex], gAlbedoImages[imageIndex].entireResourceRange()
                    )
            };
            PlortImageMemoryBarrier[] depth = new PlortImageMemoryBarrier[]{
                    new PlortImageMemoryBarrier(
                            VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT,
                            PlortImage.Layout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL,
                            VK_QUEUE_FAMILY_IGNORED, VK_QUEUE_FAMILY_IGNORED,
                            gDepthImages[imageIndex], gDepthImages[imageIndex].entireResourceRange()
                    )
            };

            cmdBuffer.pipelineBarrier(stack, PipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT, PipelineStage.FRAGMENT_SHADER_BIT | PipelineStage.COMPUTE_SHADER_BIT, 0, null, null, color);
            cmdBuffer.pipelineBarrier(stack, PipelineStage.LATE_FRAGMENT_TESTS_BIT, PipelineStage.FRAGMENT_SHADER_BIT | PipelineStage.COMPUTE_SHADER_BIT, 0, null, null, depth);
        }
    }

    public void beginSubmitPass(PlortCommandBuffer cmdBuffer, VkClearValue.Buffer clearValues, int imageIndex) {
        gbufferRenderPass.begin(cmdBuffer, clearValues, imageIndex);
    }

    public void endSubmitPass(PlortCommandBuffer cmdBuffer) {
        gbufferRenderPass.end(cmdBuffer);
    }

    public void recreate(int width, int height) {
        this.gbufferRenderPass.recreate(width, height);
    }

    @Override
    public void close() {
        gbufferPipeline.close();
        gbufferPipelineLayout.close();
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
