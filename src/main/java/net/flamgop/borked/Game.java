package net.flamgop.borked;

import net.flamgop.borked.math.Quaternionf;
import net.flamgop.borked.math.Vector2f;
import net.flamgop.borked.math.Vector3i;
import net.flamgop.borked.renderer.descriptor.*;
import net.flamgop.borked.renderer.PlortEngine;
import net.flamgop.borked.renderer.model.PlortModel;
import net.flamgop.borked.renderer.renderpass.*;
import net.flamgop.borked.renderer.image.*;
import net.flamgop.borked.renderer.material.PlortTexture;
import net.flamgop.borked.renderer.memory.*;
import net.flamgop.borked.renderer.pipeline.*;
import net.flamgop.borked.renderer.text.Atlas;
import net.flamgop.borked.renderer.text.Text;
import net.flamgop.borked.renderer.text.TextRenderer;
import net.flamgop.borked.renderer.util.ResourceHelper;
import net.flamgop.borked.renderer.util.VkUtil;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.VK13.*;

public class Game {

    private static final Logger LOGGER = LoggerFactory.getLogger(Game.class);

    private final PlortEngine engine;

    private final long[] swapchainViews;
    private final PlortImage[] mainDepthBuffers;
    private final PlortRenderPass mainRenderPass;

    private final TextRenderer textRenderer;
    private final Atlas atlas;

    private final BufferedObject<PlortBuffer> textBuffers;

    private final GBuffer gbuffer;

    private final PlortShaderModule meshModule;
    private final PlortDescriptorSetLayout meshLayout;
    private final PlortBufferedDescriptorSetPool meshDescriptors;
    private final PlortPipeline meshPipeline;

    private final PlortTexture noiseTexture;

    private PlortTexture ssaoTexture;
    private final PlortShaderModule ssaoModule;
    private final PlortDescriptorSetLayout ssaoLayout;
    private final PlortBufferedDescriptorSetPool ssaoDescriptors;
    private final PlortPipeline ssaoPipeline;

    private final CameraController cameraController;

    private final PlortBuffer metaBuffer;
    private final PlortBuffer sceneBuffer;

    private final List<Entity> entities = new ArrayList<>();

    public Game() {
        LOGGER.debug("This is a debug string");
        LOGGER.info("This is an info string");
        LOGGER.warn("This is a warning string");
        LOGGER.error("This is an error string");

        this.engine = new PlortEngine("Game", VkUtil.makeApiVersion(1,0,0,0));
        engine.onSwapchainInvalidate(this::onSwapchainInvalidate);

        engine.swapchain().label("Main");

        swapchainViews = new long[engine.swapchain().imageCount()];
        mainDepthBuffers = new PlortImage[engine.swapchain().imageCount()];

        this.mainRenderPass = new PlortRenderPass(engine.device(),
            engine.swapchain().imageCount(),
            List.of(
                new PlortAttachment(
                        ImageFormat.valueOf(engine.swapchain().format()), VK_SAMPLE_COUNT_1_BIT,
                        AttachmentLoadOp.CLEAR, AttachmentStoreOp.STORE,
                        AttachmentLoadOp.DONT_CARE, AttachmentStoreOp.DONT_CARE,
                        PlortImage.Layout.UNDEFINED, PlortImage.Layout.PRESENT_SRC_KHR,
                        (w, h, f) -> {
                            if (swapchainViews[f] != 0) PlortImage.destroyView(engine.device(), swapchainViews[f]);
                            swapchainViews[f] = PlortImage.createView(engine.device(), engine.swapchain().image(f).handle(), PlortImage.ViewType.TYPE_2D, ImageFormat.valueOf(engine.swapchain().format()), AspectMask.COLOR_BIT, 1, 1);
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
                                    engine.device(), engine.allocator(),
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
        mainRenderPass.recreate(engine.swapchain().extent().x(), engine.swapchain().extent().y());
        mainRenderPass.label("Main");

        gbuffer = new GBuffer(engine, mainRenderPass);

        this.textRenderer = new TextRenderer(engine.device(), engine.swapchain(), mainRenderPass, engine.swapchain().imageCount());
        atlas = new Atlas(engine.device(), engine.allocator(), engine.commandPool(), "assets/fonts/nunito");

        for (int i = 0; i < engine.swapchain().imageCount(); i++) {
            this.textRenderer.switchAtlas(atlas, i);
        }

        textBuffers = new BufferedObject<>(PlortBuffer.class, engine.swapchain().imageCount(), (i) -> atlas.buildTextBuffer(List.of(
                new Text(String.format("FPS: %.3f", 0f), Colors.red(), new Vector2f(0, 64), 0.5f),
                new Text("Here's another line of even cooler text", Colors.blue(), new Vector2f(0, 64 + atlas.lineHeight() * 0.5f), 0.5f),
                new Text("And another line of yet cooler text", Colors.green(), new Vector2f(0, 64 + 2 * atlas.lineHeight() * 0.5f), 0.5f)
        )));

        ByteBuffer shaderCode = ResourceHelper.loadFromResource("assets/shaders/mesh.spv");
        this.meshModule = new PlortShaderModule(engine.device(), shaderCode);
        meshModule.label("Mesh");
        MemoryUtil.memFree(shaderCode);

        this.meshLayout = new PlortDescriptorSetLayout(
                engine.device(),
                new PlortDescriptor(PlortDescriptor.Type.UNIFORM_BUFFER, 1, PlortShaderStage.Stage.ALL.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit())
        );
        this.meshDescriptors = new PlortBufferedDescriptorSetPool(engine.device(), meshLayout, 1, engine.swapchain().imageCount());

        this.meshPipeline = PlortPipeline.builder(engine.device(), gbuffer.renderPass())
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.MESH, meshModule, "meshMain"))
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.FRAGMENT, meshModule, "fragmentMain"))
                .pushConstant(new PlortPushConstant(0, 4 * Long.BYTES, PlortShaderStage.Stage.ALL.bit()))
                .descriptorSetLayouts(meshLayout)
                .blendState(PlortBlendState.disabled())
                .blendState(PlortBlendState.disabled())
                .blendState(PlortBlendState.disabled())
                .buildGraphics();

        this.cameraController = new CameraController(engine.allocator(), engine.window(), 90, 0.1f);

        sceneBuffer = new PlortBuffer(Long.BYTES, BufferUsage.UNIFORM_BUFFER_BIT, engine.allocator());
        metaBuffer = new PlortBuffer(2 * Integer.BYTES, BufferUsage.UNIFORM_BUFFER_BIT, engine.allocator());
        try (MappedMemory mem = metaBuffer.map()) {
            mem.putInt(engine.swapchain().extent().x());
            mem.putInt(engine.swapchain().extent().y());
        }

        noiseTexture = ResourceHelper.loadTextureFromResources(engine, "assets/textures/noise.png");

        ByteBuffer ssaoCode = ResourceHelper.loadFromResource("assets/shaders/ssao.spv");
        this.ssaoModule = new PlortShaderModule(engine.device(), ssaoCode);
        ssaoModule.label("SSAO");
        MemoryUtil.memFree(ssaoCode);

        this.ssaoLayout = new PlortDescriptorSetLayout(
                engine.device(),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.COMPUTE.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.COMPUTE.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.COMPUTE.bit()),
                new PlortDescriptor(PlortDescriptor.Type.UNIFORM_BUFFER, 1, PlortShaderStage.Stage.COMPUTE.bit()),
                new PlortDescriptor(PlortDescriptor.Type.STORAGE_IMAGE, 1, PlortShaderStage.Stage.COMPUTE.bit())
        );
        this.ssaoDescriptors = new PlortBufferedDescriptorSetPool(engine.device(), ssaoLayout, 1, engine.swapchain().imageCount());

        this.ssaoPipeline = PlortPipeline.builder(engine.device())
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.COMPUTE, ssaoModule, "main"))
                .descriptorSetLayouts(ssaoLayout)
                .buildCompute();

        this.ssaoTexture = new PlortTexture(
                new PlortImage(
                        engine.device(),
                        engine.allocator(),
                        PlortImage.Type.TYPE_2D,
                        new Vector3i(engine.swapchain().extent().x(), engine.swapchain().extent().y(), 1),
                        1, 1, ImageFormat.R8_UNORM,
                        PlortImage.Layout.UNDEFINED,
                        ImageUsage.STORAGE_BIT | ImageUsage.SAMPLED_BIT, 1,
                        SharingMode.EXCLUSIVE,
                        MemoryUsage.GPU_ONLY,
                        PlortImage.ViewType.TYPE_2D,
                        AspectMask.COLOR_BIT
                        ),
                new PlortSampler(engine.device(), PlortSampler.Filter.NEAREST, PlortSampler.Filter.NEAREST, PlortSampler.AddressMode.CLAMP_TO_EDGE, PlortSampler.AddressMode.CLAMP_TO_EDGE, PlortSampler.AddressMode.CLAMP_TO_EDGE)
        );

        this.entities.add(new Entity(new PlortModel(engine, "ssao_test.glb"), engine.allocator()));
    }

    public void start() {
        long prevFrameStart = System.nanoTime();
        double deltaTime;

        GLFW.glfwSetInputMode(engine.window().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);

        long timeoutTimestamp = System.nanoTime();
        boolean timeoutLastFrame = false;
        int currentFrameModInFlight = 0;
        while (engine.running()) {
            if (engine.waitForFence(currentFrameModInFlight)) {
                timeoutLastFrame = true;
                timeoutTimestamp = System.nanoTime();
                engine.device().waitIdle();
                continue;
            }
            if (timeoutLastFrame) {
                LOGGER.warn("Fence timed out for {}ms", (System.nanoTime() - timeoutTimestamp) / 1e+6);
                timeoutLastFrame = false;
            }

            long frameEnd = System.nanoTime();
            deltaTime = (frameEnd - prevFrameStart) / 1e+9;
            prevFrameStart = System.nanoTime();

            // the frame starts here

            int imageIndex = engine.acquireNextImage(currentFrameModInFlight);

            frame(deltaTime, currentFrameModInFlight, imageIndex);

            endFrame(currentFrameModInFlight, imageIndex);

            currentFrameModInFlight = (currentFrameModInFlight + 1) % engine.swapchain().imageCount();
            engine.window().pollEvents();
        }
        cleanup();
    }

    private void updateDescriptorsDeferred(int currentFrameModInFlight, int imageIndex) {
        entities.forEach(e -> e.model().setViewBuffer(engine, cameraController.viewBuffer(), currentFrameModInFlight));
    }

    private void submitDeferred(VkCommandBuffer cmdBuffer, double deltaTime, int imageIndex, int currentFrameModInFlight) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            meshPipeline.bind(cmdBuffer, PipelineBindPoint.GRAPHICS);

            double time = (GLFW.glfwGetTime() % Math.TAU);
            entities.getFirst().rotation(new Quaternionf(0f, (float) Math.sin(time / 2), 0f, (float) Math.cos(time / 2)).normalize());
            entities.forEach(e -> e.submit(cmdBuffer, meshPipeline, currentFrameModInFlight));
        }
    }

    private void updateDescriptorsShading(int currentFrameModInFlight, int imageIndex) {
        engine.device().writeDescriptorSets(List.of(
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

    private void submitShading(VkCommandBuffer cmdBuffer, double deltaTime, int imageIndex, int currentFrameModInFlight) {
        gbuffer.submitShadingPass(cmdBuffer, currentFrameModInFlight);

        textBuffers.replace(imageIndex, atlas.buildTextBuffer(List.of(
                new Text(String.format("Frame Time: %.3fms FPS: %.3f", deltaTime * 1000f, 1 / deltaTime), Colors.red(), new Vector2f(0, 64), 0.5f),
                new Text("Here's another line of even cooler text", Colors.blue(), new Vector2f(0, 64 + atlas.lineHeight() * 0.5f), 0.5f),
                new Text("And another line of yet cooler text", Colors.green(), new Vector2f(0, 64 + 2 * atlas.lineHeight() * 0.5f), 0.5f)
        )));

        textRenderer.renderTextBuffer(cmdBuffer, textBuffers.get(imageIndex), imageIndex);
    }

    private void computeSSAO(VkCommandBuffer cmdBuffer, int currentFrameModInFlight, int imageIndex) {
        engine.device().writeDescriptorSets(List.of(
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
            vkCmdBindDescriptorSets(cmdBuffer, PipelineBindPoint.COMPUTE.qualifier(), ssaoPipeline.layout(), 0, stack.longs(ssaoDescriptors.descriptorSet(currentFrameModInFlight, 0)), null);

            int groupsX = (engine.swapchain().extent().x() + 8 - 1) / 8;
            int groupsY = (engine.swapchain().extent().y() + 8 - 1) / 8;
            vkCmdDispatch(cmdBuffer, groupsX, groupsY, 1);

            ssaoTexture.image().transitionLayout(
                    cmdBuffer,
                    PlortImage.Layout.GENERAL, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL,
                    PipelineStage.COMPUTE_SHADER_BIT, PipelineStage.FRAGMENT_SHADER_BIT,
                    VK_ACCESS_SHADER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT
            );
        }
    }

    @SuppressWarnings("resource")
    private void frame(double deltaTime, int currentFrameModInFlight, int imageIndex) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default();

            VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
                    .x(0)
                    .y(0)
                    .width(engine.swapchain().extent().x())
                    .height(engine.swapchain().extent().y())
                    .minDepth(0.0f)
                    .maxDepth(1.0f);

            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack)
                    .offset(o -> o.set(0, 0))
                    .extent(e -> e.set(engine.swapchain().extent().x(), engine.swapchain().extent().y()));

            cameraController.update((float) deltaTime);

            VkCommandBuffer cmdBuffer = engine.drawBuffer(imageIndex);

            vkBeginCommandBuffer(cmdBuffer, beginInfo);

            vkCmdSetViewport(cmdBuffer, 0, viewport);
            vkCmdSetScissor(cmdBuffer, 0, scissor);

            VkClearValue.Buffer gClearValues = VkClearValue.calloc(4, stack);
            gClearValues.get(0).color().float32(0, 0).float32(1, 0).float32(2, 0).float32(3, 0);
            gClearValues.get(1).color().float32(0, 0).float32(1, 0).float32(2, 0).float32(3, 0);
            gClearValues.get(2).color().float32(0, 0).float32(1, 0).float32(2, 0).float32(3, 0);
            gClearValues.get(3).depthStencil().depth(1.0f).stencil(0);

            updateDescriptorsDeferred(currentFrameModInFlight, imageIndex);

            gbuffer.beginSubmitPass(cmdBuffer, gClearValues, imageIndex);

            submitDeferred(cmdBuffer, deltaTime, imageIndex, currentFrameModInFlight);

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

            vkEndCommandBuffer(cmdBuffer);
        }
    }

    private void endFrame(int currentFrameModInFlight, int imageIndex) {
        engine.submitFrame(currentFrameModInFlight, imageIndex);
        if (engine.presentFrame(currentFrameModInFlight, imageIndex)) {
            LOGGER.debug("Swapchain invalidated at present, skipping frame...");
        }
    }

    private void onSwapchainInvalidate() {
        this.mainRenderPass.recreate(engine.swapchain().extent().x(), engine.swapchain().extent().y());
        this.gbuffer.recreate(engine.swapchain().extent().x(), engine.swapchain().extent().y());

        this.ssaoTexture.close();
        this.ssaoTexture = new PlortTexture(
                new PlortImage(
                        engine.device(),
                        engine.allocator(),
                        PlortImage.Type.TYPE_2D,
                        new Vector3i(engine.swapchain().extent().x(), engine.swapchain().extent().y(), 1),
                        1, 1, ImageFormat.R8_UNORM,
                        PlortImage.Layout.UNDEFINED,
                        ImageUsage.STORAGE_BIT | ImageUsage.SAMPLED_BIT, 1,
                        SharingMode.EXCLUSIVE,
                        MemoryUsage.GPU_ONLY,
                        PlortImage.ViewType.TYPE_2D,
                        AspectMask.COLOR_BIT
                ),
                new PlortSampler(engine.device(), PlortSampler.Filter.LINEAR, PlortSampler.Filter.LINEAR, PlortSampler.AddressMode.CLAMP_TO_EDGE, PlortSampler.AddressMode.CLAMP_TO_EDGE, PlortSampler.AddressMode.CLAMP_TO_EDGE)
        );
        try (MappedMemory mem = metaBuffer.map()) {
            mem.putInt(engine.swapchain().extent().x());
            mem.putInt(engine.swapchain().extent().y());
        }
        cameraController.resize(engine.swapchain().extent().x(), engine.swapchain().extent().y());
    }

    public void cleanup() {
        engine.device().waitIdle();

        this.entities.forEach(Entity::close);
        PlortModel.closeNulls();

        metaBuffer.close();
        sceneBuffer.close();
        cameraController.close();

        meshPipeline.close();
        meshLayout.close();
        meshDescriptors.close();
        meshModule.close();

        ssaoTexture.close();
        ssaoPipeline.close();
        ssaoLayout.close();
        ssaoDescriptors.close();
        ssaoModule.close();

        noiseTexture.close();

        gbuffer.close();

        try { textBuffers.close(); } catch (Exception _) {} // note: exception doesn't really matter at this point, the application is closing.

        atlas.close();
        textRenderer.close();

        mainRenderPass.close();
        for (long view : swapchainViews) if (view != 0) PlortImage.destroyView(engine.device(), view);
        for (PlortImage depthImage : mainDepthBuffers) if (depthImage != null) depthImage.close();

        engine.close(); // ALWAYS LAST!!!!
    }
}
