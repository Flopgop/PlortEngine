package net.flamgop.borked;

import net.flamgop.borked.math.Vector2f;
import net.flamgop.borked.math.Vector3i;
import net.flamgop.borked.renderer.PlortCommandBuffer;
import net.flamgop.borked.renderer.PlortRenderContext;
import net.flamgop.borked.renderer.descriptor.*;
import net.flamgop.borked.renderer.image.*;
import net.flamgop.borked.renderer.material.PlortTexture;
import net.flamgop.borked.renderer.memory.*;
import net.flamgop.borked.renderer.model.PlortModel;
import net.flamgop.borked.renderer.pipeline.*;
import net.flamgop.borked.renderer.renderpass.*;
import net.flamgop.borked.renderer.text.Atlas;
import net.flamgop.borked.renderer.text.Text;
import net.flamgop.borked.renderer.text.TextRenderer;
import net.flamgop.borked.renderer.util.ResourceHelper;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkViewport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.VK_ACCESS_NONE;

@SuppressWarnings("resource")
public class Renderer implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Renderer.class);

    private final PlortRenderContext context;

    private final long[] swapchainViews;
    private final PlortImage[] mainDepthBuffers;
    private final PlortRenderPass mainRenderPass;

    private final GBuffer gbuffer;

    private final BufferedObject<PlortBuffer> textBuffers;
    private final TextRenderer textRenderer;
    private final Atlas atlas;

    private final PlortShaderModule meshModule;
    private final PlortDescriptorSetLayout meshLayout;
    private final PlortBufferedDescriptorSetPool meshDescriptors;
    private final PlortPipelineLayout meshPipelineLayout;
    private final PlortPipeline meshPipeline;

    private final PlortTexture noiseTexture;

    private PlortTexture ssaoTexture;
    private final PlortShaderModule ssaoModule;
    private final PlortDescriptorSetLayout ssaoLayout;
    private final PlortBufferedDescriptorSetPool ssaoDescriptors;
    private final PlortPipelineLayout ssaoPipelineLayout;
    private final PlortPipeline ssaoPipeline;

    private final PlortBuffer metaBuffer;
    private final PlortBuffer sceneBuffer;

    // stuff we don't manage but render
    private final CameraController cameraController;
    private final World world;


    private int currentFrameModInFlight = 0;

    // note: while we would create the context, camera controller has buffers in it so we can't.
    public Renderer(PlortRenderContext context, CameraController cameraController, World world) {
        this.cameraController = cameraController;
        this.world = world;
        this.context = context;

        context.onSwapchainInvalidate(this::onSwapchainInvalidate);
        context.swapchain().label("Main");
        swapchainViews = new long[context.swapchain().imageCount()];
        mainDepthBuffers = new PlortImage[context.swapchain().imageCount()];
        mainRenderPass = new PlortRenderPass(context.device(),
                context.swapchain().imageCount(),
                List.of(
                        new PlortAttachment(
                                ImageFormat.valueOf(context.swapchain().format()), VK_SAMPLE_COUNT_1_BIT,
                                AttachmentLoadOp.CLEAR, AttachmentStoreOp.STORE,
                                AttachmentLoadOp.DONT_CARE, AttachmentStoreOp.DONT_CARE,
                                PlortImage.Layout.UNDEFINED, PlortImage.Layout.PRESENT_SRC_KHR,
                                (w, h, f) -> {
                                    if (swapchainViews[f] != 0) PlortImage.destroyView(context.device(), swapchainViews[f]);
                                    swapchainViews[f] = PlortImage.createView(context.device(), context.swapchain().image(f).handle(), PlortImage.ViewType.TYPE_2D, ImageFormat.valueOf(context.swapchain().format()), AspectMask.COLOR_BIT, 1, 1);
                                    return swapchainViews[f];
                                }
                        ),
                        new PlortAttachment(
                                ImageFormat.D32_SFLOAT, VK_SAMPLE_COUNT_1_BIT,
                                AttachmentLoadOp.CLEAR, AttachmentStoreOp.DONT_CARE,
                                AttachmentLoadOp.DONT_CARE, AttachmentStoreOp.DONT_CARE,
                                PlortImage.Layout.UNDEFINED, PlortImage.Layout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                                (w, h, f) -> {
                                    if (mainDepthBuffers[f] != null) mainDepthBuffers[f].close();
                                    mainDepthBuffers[f] = new PlortImage(
                                            context.device(), context.allocator(),
                                            PlortImage.Type.TYPE_2D, new Vector3i(w, h, 1),
                                            1, 1, ImageFormat.D32_SFLOAT,
                                            PlortImage.Layout.UNDEFINED, ImageUsage.DEPTH_STENCIL_ATTACHMENT_BIT | ImageUsage.TRANSIENT_ATTACHMENT_BIT,
                                            VK_SAMPLE_COUNT_1_BIT, SharingMode.EXCLUSIVE, MemoryUsage.GPU_ONLY, PlortImage.ViewType.TYPE_2D, AspectMask.DEPTH_BIT
                                    );
                                    return mainDepthBuffers[f].view();
                                }
                        )
                ),
                List.of(
                        new PlortAttachmentReference(0, PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL)
                ),
                new PlortAttachmentReference(1, PlortImage.Layout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
        );
        mainRenderPass.recreate(context.swapchain().extent().x(), context.swapchain().extent().y());
        mainRenderPass.label("Main");

        gbuffer = new GBuffer(context, mainRenderPass);

        textRenderer = new TextRenderer(context.device(), context.swapchain(), mainRenderPass, context.swapchain().imageCount());
        atlas = new Atlas(context.device(), context.allocator(), context.commandPool(), "assets/fonts/nunito");

        for (int i = 0; i < context.swapchain().imageCount(); i++) {
            textRenderer.switchAtlas(atlas, i);
        }

        textBuffers = new BufferedObject<>(PlortBuffer.class, context.swapchain().imageCount(), (i) -> atlas.buildTextBuffer(List.of(
                new Text(String.format("FPS: %.3f", 0f), Colors.red(), new Vector2f(0, 64), 0.5f),
                new Text("Here's another line of even cooler text", Colors.blue(), new Vector2f(0, 64 + atlas.lineHeight() * 0.5f), 0.5f),
                new Text("And another line of yet cooler text", Colors.green(), new Vector2f(0, 64 + 2 * atlas.lineHeight() * 0.5f), 0.5f)
        )));

        ByteBuffer shaderCode = ResourceHelper.loadFromResource("assets/shaders/mesh.spv");
        this.meshModule = new PlortShaderModule(context.device(), shaderCode);
        meshModule.label("Mesh");
        MemoryUtil.memFree(shaderCode);

        this.meshLayout = new PlortDescriptorSetLayout(
                context.device(),
                new PlortDescriptor(PlortDescriptor.Type.UNIFORM_BUFFER, 1, PlortShaderStage.Stage.ALL.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit())
        );
        this.meshDescriptors = new PlortBufferedDescriptorSetPool(context.device(), meshLayout, 1, context.swapchain().imageCount());

        this.meshPipelineLayout = PlortPipelineLayout.builder(context.device())
                .pushConstant(new PlortPushConstant(0, 4 * Long.BYTES, PlortShaderStage.Stage.ALL.bit()))
                .descriptorSetLayouts(meshLayout)
                .build();
        this.meshPipeline = PlortPipeline.builder(context.device(), gbuffer.renderPass())
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.MESH, meshModule, "meshMain"))
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.FRAGMENT, meshModule, "fragmentMain"))
                .layout(meshPipelineLayout)
                .blendState(PlortBlendState.disabled())
                .blendState(PlortBlendState.disabled())
                .blendState(PlortBlendState.disabled())
                .buildGraphics();

        sceneBuffer = new PlortBuffer(Long.BYTES, BufferUsage.UNIFORM_BUFFER_BIT, context.allocator());
        metaBuffer = new PlortBuffer(2 * Integer.BYTES, BufferUsage.UNIFORM_BUFFER_BIT, context.allocator());
        try (MappedMemory mem = metaBuffer.map()) {
            mem.putInt(context.swapchain().extent().x());
            mem.putInt(context.swapchain().extent().y());
        }

        noiseTexture = ResourceHelper.loadTextureFromResources(context, "assets/textures/noise.png");

        ByteBuffer ssaoCode = ResourceHelper.loadFromResource("assets/shaders/ssao.spv");
        this.ssaoModule = new PlortShaderModule(context.device(), ssaoCode);
        ssaoModule.label("SSAO");
        MemoryUtil.memFree(ssaoCode);

        this.ssaoLayout = new PlortDescriptorSetLayout(
                context.device(),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.COMPUTE.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.COMPUTE.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.COMPUTE.bit()),
                new PlortDescriptor(PlortDescriptor.Type.UNIFORM_BUFFER, 1, PlortShaderStage.Stage.COMPUTE.bit()),
                new PlortDescriptor(PlortDescriptor.Type.STORAGE_IMAGE, 1, PlortShaderStage.Stage.COMPUTE.bit())
        );
        this.ssaoDescriptors = new PlortBufferedDescriptorSetPool(context.device(), ssaoLayout, 1, context.swapchain().imageCount());

        this.ssaoPipelineLayout = PlortPipelineLayout.builder(context.device())
                .descriptorSetLayouts(ssaoLayout)
                .build();
        this.ssaoPipeline = PlortPipeline.builder(context.device())
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.COMPUTE, ssaoModule, "main"))
                .layout(ssaoPipelineLayout)
                .buildCompute();

        this.ssaoTexture = new PlortTexture(
                new PlortImage(
                        context.device(), context.allocator(),
                        PlortImage.Type.TYPE_2D, new Vector3i(context.swapchain().extent().x(), context.swapchain().extent().y(), 1),
                        1, 1, ImageFormat.R8_UNORM,
                        PlortImage.Layout.UNDEFINED, ImageUsage.STORAGE_BIT | ImageUsage.SAMPLED_BIT, 1,
                        SharingMode.EXCLUSIVE, MemoryUsage.GPU_ONLY, PlortImage.ViewType.TYPE_2D, AspectMask.COLOR_BIT
                ),
                new PlortSampler(context.device(), PlortFilter.NEAREST, PlortFilter.NEAREST, PlortSampler.AddressMode.CLAMP_TO_EDGE, PlortSampler.AddressMode.CLAMP_TO_EDGE, PlortSampler.AddressMode.CLAMP_TO_EDGE)
        );
    }

    public boolean windowOpen() {
        return context.running();
    }

    public void waitIdle() {
        context.device().waitIdle();
    }

    private void onSwapchainInvalidate() {
        this.mainRenderPass.recreate(context.swapchain().extent().x(), context.swapchain().extent().y());
        this.gbuffer.recreate(context.swapchain().extent().x(), context.swapchain().extent().y());

        this.ssaoTexture.close();
        this.ssaoTexture = new PlortTexture(
                new PlortImage(
                        context.device(), context.allocator(),
                        PlortImage.Type.TYPE_2D, new Vector3i(context.swapchain().extent().x(), context.swapchain().extent().y(), 1),
                        1, 1, ImageFormat.R8_UNORM,
                        PlortImage.Layout.UNDEFINED, ImageUsage.STORAGE_BIT | ImageUsage.SAMPLED_BIT, 1,
                        SharingMode.EXCLUSIVE, MemoryUsage.GPU_ONLY, PlortImage.ViewType.TYPE_2D, AspectMask.COLOR_BIT
                ),
                new PlortSampler(context.device(), PlortFilter.LINEAR, PlortFilter.LINEAR, PlortSampler.AddressMode.CLAMP_TO_EDGE, PlortSampler.AddressMode.CLAMP_TO_EDGE, PlortSampler.AddressMode.CLAMP_TO_EDGE)
        );
        try (MappedMemory mem = metaBuffer.map()) {
            mem.putInt(context.swapchain().extent().x());
            mem.putInt(context.swapchain().extent().y());
        }
        cameraController.resize(context.swapchain().extent().x(), context.swapchain().extent().y());
    }

    long timeoutTimestamp = System.nanoTime();
    boolean timeoutLastFrame = false;

    private void updateDescriptorsDeferred(int imageIndex) {
        world.entities.forEach(e -> e.model().setViewBuffer(context, cameraController.viewBuffer(), currentFrameModInFlight));
    }

    private void submitDeferred(PlortCommandBuffer cmdBuffer, int imageIndex) {
        meshPipeline.bind(cmdBuffer, PipelineBindPoint.GRAPHICS);
        world.entities.forEach(e -> e.submit(cmdBuffer, meshPipelineLayout, currentFrameModInFlight));
    }

    private void updateDescriptorsShading(int currentFrameModInFlight, int imageIndex) {
        context.device().writeDescriptorSets(List.of(
                new TextureDescriptorWrite(new PlortTexture[]{gbuffer.positionTexture(imageIndex)}, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, 0, gbuffer.descriptors().descriptorSet(currentFrameModInFlight, 0)),
                new TextureDescriptorWrite(new PlortTexture[]{gbuffer.normalTexture(imageIndex)}, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, 1, gbuffer.descriptors().descriptorSet(currentFrameModInFlight, 0)),
                new TextureDescriptorWrite(new PlortTexture[]{gbuffer.albedoTexture(imageIndex)}, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, 2, gbuffer.descriptors().descriptorSet(currentFrameModInFlight, 0)),
                new TextureDescriptorWrite(new PlortTexture[]{gbuffer.depthTexture(imageIndex)}, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, 3, gbuffer.descriptors().descriptorSet(currentFrameModInFlight, 0)),
                new TextureDescriptorWrite(new PlortTexture[]{noiseTexture}, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, 4, gbuffer.descriptors().descriptorSet(currentFrameModInFlight, 0)),
                new TextureDescriptorWrite(new PlortTexture[]{ssaoTexture}, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, 5, gbuffer.descriptors().descriptorSet(currentFrameModInFlight, 0)),
                new BufferDescriptorWrite(List.of(cameraController.viewBuffer()), PlortDescriptor.Type.UNIFORM_BUFFER, 6, gbuffer.descriptors().descriptorSet(currentFrameModInFlight, 0)) ,
                new BufferDescriptorWrite(List.of(metaBuffer), PlortDescriptor.Type.UNIFORM_BUFFER, 7, gbuffer.descriptors().descriptorSet(currentFrameModInFlight, 0))
        ));
    }

    private void submitShading(PlortCommandBuffer cmdBuffer, double deltaTime, int imageIndex, int currentFrameModInFlight) {
        gbuffer.submitShadingPass(cmdBuffer, currentFrameModInFlight);

        textBuffers.replace(imageIndex, atlas.buildTextBuffer(List.of(
                new Text(String.format("Frame Time: %.3fms FPS: %.3f", deltaTime * 1000f, 1 / deltaTime), Colors.red(), new Vector2f(0, 64), 0.5f),
                new Text("Here's another line of even cooler text", Colors.blue(), new Vector2f(0, 64 + atlas.lineHeight() * 0.5f), 0.5f),
                new Text("And another line of yet cooler text", Colors.green(), new Vector2f(0, 64 + 2 * atlas.lineHeight() * 0.5f), 0.5f)
        )));

        textRenderer.renderTextBuffer(cmdBuffer, textBuffers.get(imageIndex), imageIndex);
    }

    private void computeSSAO(PlortCommandBuffer cmdBuffer, int currentFrameModInFlight, int imageIndex) {
        context.device().writeDescriptorSets(List.of(
                new TextureDescriptorWrite(new PlortTexture[]{gbuffer.positionTexture(imageIndex)}, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, 0, ssaoDescriptors.descriptorSet(currentFrameModInFlight, 0)),
                new TextureDescriptorWrite(new PlortTexture[]{gbuffer.normalTexture(imageIndex)}, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, 1, ssaoDescriptors.descriptorSet(currentFrameModInFlight, 0)),
                new TextureDescriptorWrite(new PlortTexture[]{noiseTexture}, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, 2, ssaoDescriptors.descriptorSet(currentFrameModInFlight, 0)),
                new BufferDescriptorWrite(List.of(cameraController.viewBuffer()), PlortDescriptor.Type.UNIFORM_BUFFER, 3, ssaoDescriptors.descriptorSet(currentFrameModInFlight, 0)),
                new TextureDescriptorWrite(new PlortImage[]{ssaoTexture.image()}, PlortImage.Layout.GENERAL, 4, ssaoDescriptors.descriptorSet(currentFrameModInFlight, 0))
        ));

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ssaoTexture.image().transitionLayout(
                    cmdBuffer,
                    PlortImage.Layout.UNDEFINED, PlortImage.Layout.GENERAL,
                    PipelineStage.TOP_OF_PIPE_BIT, PipelineStage.COMPUTE_SHADER_BIT,
                    VK_ACCESS_NONE, VK_ACCESS_SHADER_WRITE_BIT
            );

            ssaoPipeline.bind(cmdBuffer, PipelineBindPoint.COMPUTE);
            cmdBuffer.bindDescriptorSets(PipelineBindPoint.COMPUTE, ssaoPipelineLayout, 0, stack.longs(ssaoDescriptors.descriptorSet(currentFrameModInFlight, 0)), null);

            int groupsX = (context.swapchain().extent().x() + 8 - 1) / 8;
            int groupsY = (context.swapchain().extent().y() + 8 - 1) / 8;
            cmdBuffer.dispatch(groupsX, groupsY, 1);

            ssaoTexture.image().transitionLayout(
                    cmdBuffer,
                    PlortImage.Layout.GENERAL, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL,
                    PipelineStage.COMPUTE_SHADER_BIT, PipelineStage.FRAGMENT_SHADER_BIT,
                    VK_ACCESS_SHADER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT
            );
        }
    }

    public boolean frame(double deltaTime) {
        if (context.waitForFence(currentFrameModInFlight)) {
            timeoutLastFrame = true;
            timeoutTimestamp = System.nanoTime();
            context.device().waitIdle();
            return false;
        }
        if (timeoutLastFrame) {
            LOGGER.warn("Fence timed out for {}ms", (System.nanoTime() - timeoutTimestamp) / 1e+6);
            timeoutLastFrame = false;
        }

        int imageIndex = context.acquireNextImage(currentFrameModInFlight);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default();

            VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
                    .x(0)
                    .y(0)
                    .width(context.swapchain().extent().x())
                    .height(context.swapchain().extent().y())
                    .minDepth(0.0f)
                    .maxDepth(1.0f);

            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack)
                    .offset(o -> o.set(0, 0))
                    .extent(e -> e.set(context.swapchain().extent().x(), context.swapchain().extent().y()));

            try (PlortCommandBuffer cmdBuffer = new PlortCommandBuffer(context.drawBuffer(imageIndex))) {
                cmdBuffer.begin(beginInfo);
                cmdBuffer.setViewport(0, viewport);
                cmdBuffer.setScissor(0, scissor);

                VkClearValue.Buffer gClearValues = VkClearValue.calloc(4, stack);
                gClearValues.get(0).color().float32(0, 0).float32(1, 0).float32(2, 0).float32(3, 0);
                gClearValues.get(1).color().float32(0, 0).float32(1, 0).float32(2, 0).float32(3, 0);
                gClearValues.get(2).color().float32(0, 0).float32(1, 0).float32(2, 0).float32(3, 0);
                gClearValues.get(3).depthStencil().depth(1.0f).stencil(0);

                updateDescriptorsDeferred(imageIndex);

                gbuffer.beginSubmitPass(cmdBuffer, gClearValues, imageIndex);

                submitDeferred(cmdBuffer, imageIndex);

                gbuffer.endSubmitPass(cmdBuffer);

                VkClearValue.Buffer clearValues = VkClearValue.calloc(2, stack);
                clearValues.get(0).color().float32(0, 0.0595f).float32(1, 0.0595f).float32(2, 0.0595f).float32(3, 1);
                clearValues.get(1).depthStencil().depth(1.0f).stencil(0);

                gbuffer.transitionImagesForShading(cmdBuffer, imageIndex);
                computeSSAO(cmdBuffer, currentFrameModInFlight, imageIndex);
                updateDescriptorsShading(currentFrameModInFlight, imageIndex);

                mainRenderPass.begin(cmdBuffer, clearValues, imageIndex);

                submitShading(cmdBuffer, deltaTime, imageIndex, currentFrameModInFlight);

                mainRenderPass.end(cmdBuffer);
                gbuffer.transitionImagesForSubmit(cmdBuffer, imageIndex);
            }
        }

        endFrame(imageIndex);

        currentFrameModInFlight = (currentFrameModInFlight + 1) % context.swapchain().imageCount();
        context.window().pollEvents();
        return true;
    }

    private void endFrame(int imageIndex) {
        context.submitFrame(currentFrameModInFlight, imageIndex);
        if (context.presentFrame(currentFrameModInFlight, imageIndex)) {
            LOGGER.debug("Swapchain invalidated at present, skipping frame...");
        }
    }

    @Override
    public void close() {
        context.device().waitIdle();
        PlortModel.closeNulls();

        metaBuffer.close();
        sceneBuffer.close();

        meshPipeline.close();
        meshPipelineLayout.close();
        meshLayout.close();
        meshDescriptors.close();
        meshModule.close();

        ssaoTexture.close();
        ssaoPipeline.close();
        ssaoPipelineLayout.close();
        ssaoLayout.close();
        ssaoDescriptors.close();
        ssaoModule.close();

        noiseTexture.close();

        gbuffer.close();

        try { textBuffers.close(); } catch (Exception _) {} // note: this doesn't actually throw an exception because text buffers do not create exceptions when closing.

        atlas.close();
        textRenderer.close();

        mainRenderPass.close();
        for (long view : swapchainViews) if (view != 0) PlortImage.destroyView(context.device(), view);
        for (PlortImage depthImage : mainDepthBuffers) if (depthImage != null) depthImage.close();
    }
}
