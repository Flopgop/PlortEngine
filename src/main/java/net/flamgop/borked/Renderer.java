package net.flamgop.borked;

import net.flamgop.borked.camera.PlayerController;
import net.flamgop.borked.entity.EntityManager;
import net.flamgop.borked.entity.system.EntityRenderSystem;
import net.flamgop.borked.math.Matrix4f;
import net.flamgop.borked.math.Vector2f;
import net.flamgop.borked.math.Vector3i;
import net.flamgop.borked.renderer.PlortCommandBuffer;
import net.flamgop.borked.renderer.PlortRenderContext;
import net.flamgop.borked.renderer.descriptor.*;
import net.flamgop.borked.renderer.image.*;
import net.flamgop.borked.renderer.material.PlortTexture;
import net.flamgop.borked.renderer.memory.*;
import net.flamgop.borked.model.PlortModel;
import net.flamgop.borked.renderer.pipeline.*;
import net.flamgop.borked.renderer.pipeline.barrier.PlortImageMemoryBarrier;
import net.flamgop.borked.renderer.renderpass.*;
import net.flamgop.borked.text.Atlas;
import net.flamgop.borked.text.Text;
import net.flamgop.borked.text.TextRenderer;
import net.flamgop.borked.renderer.util.ResourceHelper;
import net.flamgop.borked.util.Colors;
import net.flamgop.borked.world.SceneData;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkViewport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.VK_ACCESS_NONE;

@SuppressWarnings("resource")
public class Renderer implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Renderer.class);

    private final PlortRenderContext context;

    private final long[] swapchainViews;
    private final PlortRenderPass postRenderPass;
    private final PlortShaderModule postModule;
    private final PlortDescriptorSetLayout postLayout;
    private final PlortBufferedDescriptorSetPool postDescriptors;
    private final PlortPipelineLayout postPipelineLayout;
    private final PlortPipeline postPipeline;
    private final PlortSampler postSampler;
    private final PlortBuffer postDrawDataBuffer;

    private final PlortImage[] mainColorTextures;
    private final PlortRenderPass mainRenderPass;

    private final GBuffer gbuffer;

    private final BufferedObject<PlortBuffer> dynamicTextBuffers;
    private final PlortBuffer staticTextBuffer;
    private final TextRenderer textRenderer;
    private final Atlas atlas;

    private final PlortTexture noiseTexture;

    private final PlortImage[] ssaoTargetImages;
    private final PlortSampler ssaoSampler;
    private final PlortShaderModule ssaoModule;
    private final PlortDescriptorSetLayout ssaoLayout;
    private final PlortBufferedDescriptorSetPool ssaoDescriptors;
    private final PlortPipelineLayout ssaoPipelineLayout;
    private final PlortPipeline ssaoPipeline;

    private final PlortShaderModule aabbModule;
    private final PlortDescriptorSetLayout aabbLayout;
    private final PlortBufferedDescriptorSetPool aabbDescriptors;
    private final PlortPipelineLayout aabbPipelineLayout;
    private final PlortPipeline aabbPipeline;

    private final PlortImage[] sceneShadowMaps;
    private final PlortSampler shadowSampler;
    private final PlortRenderPass sceneShadowPass;
    private final PlortImage[] playerShadowMaps;
    private final PlortRenderPass playerShadowPass;

    // these don't change often or ever, so we don't need to create multiple buffers here
    private final PlortBuffer metaBuffer;
    private final PlortBuffer sceneBuffer;
    private final PlortBuffer identityTransformBuffer;

    private final BufferedObject<PlortBuffer> shadowInfoBuffers;

    // stuff we don't manage but render
    private final GameState gameState;
    private final ShadowManager shadowManager;
    private final PlayerController playerController;
    private final EntityManager entityManager;

    private int currentImageIndex = 0;
    private int currentFrameModInFlight = 0;

    // note: while we would create the context, camera controller has buffers in it so we can't.
    public Renderer(GameState gameState, PlortRenderContext context, PlayerController playerController, EntityManager entityManager, ShadowManager shadowManager) {
        this.gameState = gameState;
        this.context = context;
        this.playerController = playerController;
        this.entityManager = entityManager;
        this.shadowManager = shadowManager;

        context.onSwapchainInvalidate(this::onSwapchainInvalidate);
        context.swapchain().label("Main");
        swapchainViews = new long[context.swapchain().imageCount()];
        postRenderPass = new PlortRenderPass(context.device(),
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
                        )
                ),
                List.of(
                        new PlortAttachmentReference(0, PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL)
                ),
                null
        );
        postRenderPass.recreate(context.swapchain().extent().x(), context.swapchain().extent().y());
        postRenderPass.label("Post");

        ByteBuffer postCode = ResourceHelper.loadFromResource("assets/shaders/post/post.spv");
        this.postModule = new PlortShaderModule(context.device(), postCode);
        postModule.label("Post");
        MemoryUtil.memFree(postCode);

        this.postLayout = new PlortDescriptorSetLayout(
                context.device(),
                new PlortDescriptor(PlortDescriptor.Type.UNIFORM_BUFFER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit())
        );
        this.postDescriptors = new PlortBufferedDescriptorSetPool(context.device(), postLayout, 1, context.swapchain().imageCount());

        this.postPipelineLayout = PlortPipelineLayout.builder(context.device())
                .descriptorSetLayouts(postLayout)
                .build();
        this.postPipeline = PlortPipeline.builder(context.device(), postRenderPass)
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.MESH, postModule, "mesh_main"))
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.FRAGMENT, postModule, "fragment_main"))
                .layout(postPipelineLayout)
                .blendState(PlortBlendState.disabled())
                .buildGraphics();

        this.postSampler = new PlortSampler(context.device(), PlortFilter.NEAREST, PlortFilter.NEAREST, PlortSampler.AddressMode.CLAMP_TO_EDGE, PlortSampler.AddressMode.CLAMP_TO_EDGE, PlortSampler.AddressMode.CLAMP_TO_EDGE);

        this.postDrawDataBuffer = new PlortBuffer(2 * Long.BYTES, BufferUsage.UNIFORM_BUFFER_BIT, context.allocator());

        mainColorTextures = new PlortImage[context.swapchain().imageCount()];
        mainRenderPass = new PlortRenderPass(context.device(),
                context.swapchain().imageCount(),
                List.of(
                        new PlortAttachment(
                                ImageFormat.R16G16B16A16_SFLOAT, VK_SAMPLE_COUNT_1_BIT,
                                AttachmentLoadOp.CLEAR, AttachmentStoreOp.STORE,
                                AttachmentLoadOp.DONT_CARE, AttachmentStoreOp.DONT_CARE,
                                PlortImage.Layout.UNDEFINED, PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL,
                                (w, h, f) -> {
                                    if (mainColorTextures[f] != null) mainColorTextures[f].close();
                                    mainColorTextures[f] = new PlortImage(context.device(), context.allocator(),
                                            PlortImage.Type.TYPE_2D, new Vector3i(w, h, 1),
                                            1, 1, ImageFormat.R16G16B16A16_SFLOAT, PlortImage.Layout.UNDEFINED,
                                            ImageUsage.COLOR_ATTACHMENT_BIT | ImageUsage.SAMPLED_BIT, VK_SAMPLE_COUNT_1_BIT,
                                            SharingMode.EXCLUSIVE, MemoryUsage.GPU_ONLY, PlortImage.ViewType.TYPE_2D, AspectMask.COLOR_BIT
                                    );
                                    return mainColorTextures[f].view();
                                }
                        )
                ),
                List.of(
                        new PlortAttachmentReference(0, PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL)
                ),
                null
        );
        mainRenderPass.recreate(context.swapchain().extent().x(), context.swapchain().extent().y());
        mainRenderPass.label("Main");

        gbuffer = new GBuffer(context, mainRenderPass);

        textRenderer = new TextRenderer(context.device(), context.swapchain(), postRenderPass, context.swapchain().imageCount());
        try {
            atlas = new Atlas(context.device(), context.allocator(), context.commandPool(), "assets/fonts/nunito");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        for (int i = 0; i < context.swapchain().imageCount(); i++) {
            textRenderer.switchAtlas(atlas, i);
        }

        dynamicTextBuffers = new BufferedObject<>(PlortBuffer.class, context.swapchain().imageCount(), (_) -> atlas.buildTextBuffer(List.of(
                new Text(String.format("FPS: %.3f", 0f), Colors.red(), new Vector2f(0, 64), 0.5f)
        )));

        staticTextBuffer = atlas.buildTextBuffer(List.of(
                new Text("Here's another line of even cooler text", Colors.blue(), new Vector2f(0, 64 + atlas.lineHeight() * 0.5f), 0.5f),
                new Text("And another line of yet cooler text", Colors.green(), new Vector2f(0, 64 + 2 * atlas.lineHeight() * 0.5f), 0.5f)
        ));

        sceneBuffer = new PlortBuffer(Long.BYTES, BufferUsage.UNIFORM_BUFFER_BIT, context.allocator());
        shadowInfoBuffers = new BufferedObject<>(PlortBuffer.class, context.swapchain().imageCount(), _ -> new PlortBuffer(2 * Long.BYTES, BufferUsage.UNIFORM_BUFFER_BIT, context.allocator()));
        metaBuffer = new PlortBuffer(2 * Integer.BYTES, BufferUsage.UNIFORM_BUFFER_BIT, context.allocator());
        try (MappedMemory mem = metaBuffer.map()) {
            mem.putInt(context.swapchain().extent().x());
            mem.putInt(context.swapchain().extent().y());
        }
        identityTransformBuffer = new PlortBuffer(2 * Matrix4f.BYTES, BufferUsage.STORAGE_BUFFER_BIT, context.allocator());
        try (MappedMemory mem = identityTransformBuffer.map()) {
            mem.putMatrix4f(new Matrix4f());
            mem.putMatrix4f(new Matrix4f().invert());
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

        this.ssaoSampler = new PlortSampler(context.device(), PlortFilter.NEAREST, PlortFilter.NEAREST, PlortSampler.AddressMode.CLAMP_TO_EDGE, PlortSampler.AddressMode.CLAMP_TO_EDGE, PlortSampler.AddressMode.CLAMP_TO_EDGE);
        this.ssaoTargetImages = new PlortImage[context.swapchain().imageCount()];
        for (int i = 0; i < context.swapchain().imageCount(); i++) {
            ssaoTargetImages[i] = new PlortImage(
                    context.device(), context.allocator(),
                    PlortImage.Type.TYPE_2D, new Vector3i(context.swapchain().extent().x(), context.swapchain().extent().y(), 1),
                    1, 1, ImageFormat.R8_UNORM,
                    PlortImage.Layout.UNDEFINED, ImageUsage.STORAGE_BIT | ImageUsage.SAMPLED_BIT, 1,
                    SharingMode.EXCLUSIVE, MemoryUsage.GPU_ONLY, PlortImage.ViewType.TYPE_2D, AspectMask.COLOR_BIT
            );
        }

        ByteBuffer aabbCode = ResourceHelper.loadFromResource("assets/shaders/aabb.spv");
        this.aabbModule = new PlortShaderModule(context.device(), aabbCode);
        aabbModule.label("AABB");
        MemoryUtil.memFree(aabbCode);

        this.aabbLayout = new PlortDescriptorSetLayout(
                context.device(),
                new PlortDescriptor(PlortDescriptor.Type.UNIFORM_BUFFER, 1, PlortShaderStage.Stage.MESH.bit())
        );
        this.aabbDescriptors = new PlortBufferedDescriptorSetPool(context.device(), aabbLayout, 1, context.swapchain().imageCount());

        this.aabbPipelineLayout = PlortPipelineLayout.builder(context.device())
                .descriptorSetLayouts(aabbLayout)
                .pushConstant(new PlortPushConstant(0, Long.BYTES, PlortShaderStage.Stage.MESH.bit()))
                .build();
        this.aabbPipeline = PlortPipeline.builder(context.device(), mainRenderPass)
                .layout(aabbPipelineLayout)
                .blendState(PlortBlendState.disabled())
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.MESH, aabbModule, "meshMain"))
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.FRAGMENT, aabbModule, "fragmentMain"))
                .depthStencilStateInfo(new PlortDepthStencilState(false, false, CompareOp.ALWAYS, false, false, new PlortDepthStencilState.StencilOpState(), new PlortDepthStencilState.StencilOpState(), 0f, 1f))
                .buildGraphics();

        this.sceneShadowMaps = new PlortImage[context.swapchain().imageCount()];
        this.shadowSampler = new PlortSampler(context.device(), PlortFilter.LINEAR, PlortFilter.LINEAR,
                PlortSampler.AddressMode.CLAMP_TO_BORDER,
                PlortSampler.AddressMode.CLAMP_TO_BORDER,
                PlortSampler.AddressMode.CLAMP_TO_BORDER);

        this.sceneShadowPass = new PlortRenderPass(context.device(),
                context.swapchain().imageCount(),
                List.of(
                        new PlortAttachment(
                                ImageFormat.D32_SFLOAT, VK_SAMPLE_COUNT_1_BIT,
                                AttachmentLoadOp.CLEAR, AttachmentStoreOp.STORE,
                                AttachmentLoadOp.DONT_CARE, AttachmentStoreOp.DONT_CARE,
                                PlortImage.Layout.UNDEFINED, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL,
                                (w,h,f) -> {
                                    if (sceneShadowMaps[f] != null) sceneShadowMaps[f].close();
                                    sceneShadowMaps[f] = new PlortImage(
                                            context.device(), context.allocator(),
                                            PlortImage.Type.TYPE_2D, new Vector3i(w, h, 1),
                                            1, 1, ImageFormat.D32_SFLOAT,
                                            PlortImage.Layout.UNDEFINED,
                                            ImageUsage.DEPTH_STENCIL_ATTACHMENT_BIT | ImageUsage.SAMPLED_BIT,
                                            VK_SAMPLE_COUNT_1_BIT, SharingMode.EXCLUSIVE, MemoryUsage.GPU_ONLY,
                                            PlortImage.ViewType.TYPE_2D, AspectMask.DEPTH_BIT
                                    );
                                    return sceneShadowMaps[f].view();
                                }
                        )
                ),
                null,
                new PlortAttachmentReference(0, PlortImage.Layout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
        );
        sceneShadowPass.recreate(4096, 4096);
        sceneShadowPass.label("Shadow");

        this.playerShadowMaps = new PlortImage[context.swapchain().imageCount()];

        this.playerShadowPass = new PlortRenderPass(context.device(),
                context.swapchain().imageCount(),
                List.of(
                        new PlortAttachment(
                                ImageFormat.D32_SFLOAT, VK_SAMPLE_COUNT_1_BIT,
                                AttachmentLoadOp.CLEAR, AttachmentStoreOp.STORE,
                                AttachmentLoadOp.DONT_CARE, AttachmentStoreOp.DONT_CARE,
                                PlortImage.Layout.UNDEFINED, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL,
                                (w,h,f) -> {
                                    if (playerShadowMaps[f] != null) playerShadowMaps[f].close();
                                    playerShadowMaps[f] = new PlortImage(
                                            context.device(), context.allocator(),
                                            PlortImage.Type.TYPE_2D, new Vector3i(w, h, 1),
                                            1, 1, ImageFormat.D32_SFLOAT,
                                            PlortImage.Layout.UNDEFINED,
                                            ImageUsage.DEPTH_STENCIL_ATTACHMENT_BIT | ImageUsage.SAMPLED_BIT,
                                            VK_SAMPLE_COUNT_1_BIT, SharingMode.EXCLUSIVE, MemoryUsage.GPU_ONLY,
                                            PlortImage.ViewType.TYPE_2D, AspectMask.DEPTH_BIT
                                    );
                                    return playerShadowMaps[f].view();
                                }
                        )
                ),
                null,
                new PlortAttachmentReference(0, PlortImage.Layout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
        );
        playerShadowPass.recreate(4096, 4096);
        playerShadowPass.label("Player Shadow");

        for (int i = 0; i < context.swapchain().imageCount(); i++) {
            updateFrameDescriptors(i);
        }
    }

    public GBuffer gbuffer() {
        return gbuffer;
    }

    public PlortRenderPass shadowRenderPass() {
        return sceneShadowPass;
    }

    public void sceneData(SceneData data) {
        try (MappedMemory mem = sceneBuffer.map()) {
            mem.putLong(data.buffer().deviceAddress());
        }
    }

    private void updateFrameDescriptors(int frame) {
        try (MappedMemory mem = shadowInfoBuffers.get(frame).map()) {
            mem.putLong(shadowManager.playerShadowViewBuffer(frame).deviceAddress());
            mem.putLong(shadowManager.sceneShadowViewBuffer(frame).deviceAddress());
        }
        playerController.setupViewBuffers(frame, shadowManager.playerShadowViewBuffer(frame));
        context.device().writeDescriptorSets(List.of(
                new BufferDescriptorWrite(List.of(playerController.viewBuffer(frame)), PlortDescriptor.Type.UNIFORM_BUFFER, 0, aabbDescriptors.descriptorSet(frame, 0))
        ));
        context.device().writeDescriptorSets(List.of(
                new TextureDescriptorWrite(new PlortTexture[]{gbuffer.positionTexture(frame)}, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, 0, gbuffer.descriptors().descriptorSet(frame, 0)),
                new TextureDescriptorWrite(new PlortTexture[]{gbuffer.normalTexture(frame)}, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, 1, gbuffer.descriptors().descriptorSet(frame, 0)),
                new TextureDescriptorWrite(new PlortTexture[]{gbuffer.albedoTexture(frame)}, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, 2, gbuffer.descriptors().descriptorSet(frame, 0)),
                new TextureDescriptorWrite(new PlortTexture[]{gbuffer.depthTexture(frame)}, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, 3, gbuffer.descriptors().descriptorSet(frame, 0)),
                new TextureDescriptorWrite(new PlortTexture[]{new PlortTexture(sceneShadowMaps[frame], shadowSampler)}, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, 6, gbuffer.descriptors().descriptorSet(frame, 0)),
                new TextureDescriptorWrite(new PlortTexture[]{new PlortTexture(playerShadowMaps[frame], shadowSampler)}, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, 7, gbuffer.descriptors().descriptorSet(frame, 0)),

                new TextureDescriptorWrite(new PlortTexture[]{noiseTexture}, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, 4, gbuffer.descriptors().descriptorSet(frame, 0)),
                new TextureDescriptorWrite(new PlortTexture[]{new PlortTexture(ssaoTargetImages[frame], ssaoSampler)}, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, 5, gbuffer.descriptors().descriptorSet(frame, 0)),
                new BufferDescriptorWrite(List.of(playerController.viewBuffer(frame)), PlortDescriptor.Type.UNIFORM_BUFFER, 8, gbuffer.descriptors().descriptorSet(frame, 0)),
                new BufferDescriptorWrite(List.of(metaBuffer), PlortDescriptor.Type.UNIFORM_BUFFER, 9, gbuffer.descriptors().descriptorSet(frame, 0)),
                new BufferDescriptorWrite(List.of(sceneBuffer), PlortDescriptor.Type.UNIFORM_BUFFER, 10, gbuffer.descriptors().descriptorSet(frame, 0)),
                new BufferDescriptorWrite(List.of(shadowInfoBuffers.get(frame)), PlortDescriptor.Type.UNIFORM_BUFFER, 11, gbuffer.descriptors().descriptorSet(frame, 0))
        ));

        context.device().writeDescriptorSets(List.of(
                new TextureDescriptorWrite(new PlortTexture[]{gbuffer.positionTexture(frame)}, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, 0, ssaoDescriptors.descriptorSet(frame, 0)),
                new TextureDescriptorWrite(new PlortTexture[]{gbuffer.normalTexture(frame)}, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, 1, ssaoDescriptors.descriptorSet(frame, 0)),

                new TextureDescriptorWrite(new PlortTexture[]{noiseTexture}, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, 2, ssaoDescriptors.descriptorSet(frame, 0)),
                new BufferDescriptorWrite(List.of(playerController.viewBuffer(frame)), PlortDescriptor.Type.UNIFORM_BUFFER, 3, ssaoDescriptors.descriptorSet(frame, 0)),
                new TextureDescriptorWrite(new PlortImage[]{ssaoTargetImages[frame]}, PlortImage.Layout.GENERAL, 4, ssaoDescriptors.descriptorSet(frame, 0))
        ));

        context.device().writeDescriptorSets(List.of(
                new BufferDescriptorWrite(List.of(postDrawDataBuffer), PlortDescriptor.Type.UNIFORM_BUFFER, 0, postDescriptors.descriptorSet(frame, 0)),
                new TextureDescriptorWrite(new PlortTexture[]{new PlortTexture(mainColorTextures[frame], postSampler)}, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, 1, postDescriptors.descriptorSet(frame, 0)),
                new TextureDescriptorWrite(new PlortTexture[]{new PlortTexture(gbuffer.depth(frame), postSampler)}, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, 2, postDescriptors.descriptorSet(frame, 0))
        ));
    }

    public PlortBuffer sceneBuffer() {
        return sceneBuffer;
    }

    public int currentFrameModInFlight() {
        return currentFrameModInFlight;
    }

    public boolean windowOpen() {
        return context.running();
    }

    public void waitIdle() {
        context.device().waitIdle();
    }

    private void onSwapchainInvalidate() {
        this.mainRenderPass.recreate(context.swapchain().extent().x(), context.swapchain().extent().y());
        this.postRenderPass.recreate(context.swapchain().extent().x(), context.swapchain().extent().y());
        this.gbuffer.recreate(context.swapchain().extent().x(), context.swapchain().extent().y());

        for (int i = 0; i < context.swapchain().imageCount(); i++) {
            ssaoTargetImages[i].close();
            ssaoTargetImages[i] = new PlortImage(
                    context.device(), context.allocator(),
                    PlortImage.Type.TYPE_2D, new Vector3i(context.swapchain().extent().x(), context.swapchain().extent().y(), 1),
                    1, 1, ImageFormat.R8_UNORM,
                    PlortImage.Layout.UNDEFINED, ImageUsage.STORAGE_BIT | ImageUsage.SAMPLED_BIT, 1,
                    SharingMode.EXCLUSIVE, MemoryUsage.GPU_ONLY, PlortImage.ViewType.TYPE_2D, AspectMask.COLOR_BIT
            );
        }
        try (MappedMemory mem = metaBuffer.map()) {
            mem.putInt(context.swapchain().extent().x());
            mem.putInt(context.swapchain().extent().y());
        }
        playerController.resize(context.swapchain().extent().x(), context.swapchain().extent().y());

        for (int i = 0; i < context.swapchain().imageCount(); i++) {
            updateFrameDescriptors(i);
        }
    }

    long timeoutTimestamp = System.nanoTime();
    boolean timeoutLastFrame = false;

    private void submitDeferred(EntityRenderSystem renderSystem, PlortCommandBuffer cmdBuffer, int imageIndex) {
        renderSystem.renderPlayer(cmdBuffer, imageIndex, false);
        renderSystem.render(cmdBuffer, entityManager, imageIndex, false);
    }

    private void submitShadow(EntityRenderSystem renderSystem, PlortCommandBuffer cmdBuffer, int imageIndex) {
        renderSystem.render(cmdBuffer, entityManager, imageIndex, true);
    }

    private void submitPlayerShadow(EntityRenderSystem renderSystem, PlortCommandBuffer cmdBuffer, int imageIndex) {
        renderSystem.renderPlayer(cmdBuffer, imageIndex, true);
    }

    private void submitShading(EntityRenderSystem renderSystem, PlortCommandBuffer cmdBuffer, double deltaTime, int imageIndex) {
        gbuffer.bindDescriptorSet(cmdBuffer, imageIndex);
        gbuffer.submitShadingPass(cmdBuffer);

        if (gameState.renderDebug()) {
            aabbPipeline.bind(cmdBuffer, PipelineBindPoint.GRAPHICS);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                cmdBuffer.bindDescriptorSets(PipelineBindPoint.GRAPHICS, aabbPipelineLayout, 0, stack.longs(aabbDescriptors.descriptorSet(imageIndex, 0)), null);
                PlortBuffer buffer = renderSystem.aabbBuffer(imageIndex);
                cmdBuffer.pushConstants(aabbPipelineLayout, PlortShaderStage.Stage.MESH.bit(), 0, MemoryUtil.memByteBuffer(stack.longs(buffer != null ? buffer.deviceAddress() : 0)));
                cmdBuffer.drawMeshTasksEXT((int) renderSystem.aabbCount(), 1, 1);
            }
        }
    }

    private void computeSSAO(PlortCommandBuffer cmdBuffer, int imageIndex) {
        try (MemoryStack stack = MemoryStack.stackPush()) {

            ssaoTargetImages[imageIndex].transitionLayout(
                    cmdBuffer,
                    PlortImage.Layout.UNDEFINED, PlortImage.Layout.GENERAL,
                    PipelineStage.TOP_OF_PIPE_BIT, PipelineStage.COMPUTE_SHADER_BIT,
                    VK_ACCESS_NONE, VK_ACCESS_SHADER_WRITE_BIT
            );

            ssaoPipeline.bind(cmdBuffer, PipelineBindPoint.COMPUTE);
            cmdBuffer.bindDescriptorSets(PipelineBindPoint.COMPUTE, ssaoPipelineLayout, 0, stack.longs(ssaoDescriptors.descriptorSet(imageIndex, 0)), null);

            int groupsX = (context.swapchain().extent().x() + 8 - 1) / 8;
            int groupsY = (context.swapchain().extent().y() + 8 - 1) / 8;
            cmdBuffer.dispatch(groupsX, groupsY, 1);

            ssaoTargetImages[imageIndex].transitionLayout(
                    cmdBuffer,
                    PlortImage.Layout.GENERAL, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL,
                    PipelineStage.COMPUTE_SHADER_BIT, PipelineStage.FRAGMENT_SHADER_BIT,
                    VK_ACCESS_SHADER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT
            );
        }
    }

    public int currentImageIndex() {
        return currentImageIndex;
    }

    public int startFrame() {
        if (context.waitForFence(currentFrameModInFlight)) {
            timeoutLastFrame = true;
            timeoutTimestamp = System.nanoTime();
            context.device().waitIdle();
            return -1;
        }
        if (timeoutLastFrame) {
            LOGGER.warn("Fence timed out for {}ms", (System.nanoTime() - timeoutTimestamp) / 1e+6);
            timeoutLastFrame = false;
        }

        int imageIndex = context.acquireNextImage(currentFrameModInFlight);
        currentImageIndex = imageIndex;
        return imageIndex;
    }

    public boolean frame(EntityRenderSystem renderSystem, int imageIndex, double deltaTime) {
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

            VkViewport.Buffer shadowViewport = VkViewport.calloc(1, stack)
                    .x(0)
                    .y(0)
                    .width(4096)
                    .height(4096)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);

            VkRect2D.Buffer shadowScissor = VkRect2D.calloc(1, stack)
                    .offset(o -> o.set(0, 0))
                    .extent(e -> e.set(4096, 4096));

            try (PlortCommandBuffer cmdBuffer = new PlortCommandBuffer(context.drawBuffer(imageIndex))) {
                cmdBuffer.begin(beginInfo);

                cmdBuffer.setViewport(0, shadowViewport);
                cmdBuffer.setScissor(0, shadowScissor);

                sceneShadowMaps[imageIndex].transitionLayout(cmdBuffer, PlortImage.Layout.UNDEFINED, PlortImage.Layout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL, PipelineStage.TOP_OF_PIPE_BIT, PipelineStage.EARLY_FRAGMENT_TESTS_BIT, VK_ACCESS_NONE, VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
                sceneShadowPass.begin(cmdBuffer, VkClearValue.calloc(1, stack).depthStencil(d -> d.depth(1.0f).stencil(0)), imageIndex);
                submitShadow(renderSystem, cmdBuffer, imageIndex);
                sceneShadowPass.end(cmdBuffer);

                playerShadowMaps[imageIndex].transitionLayout(cmdBuffer, PlortImage.Layout.UNDEFINED, PlortImage.Layout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL, PipelineStage.TOP_OF_PIPE_BIT, PipelineStage.EARLY_FRAGMENT_TESTS_BIT, VK_ACCESS_NONE, VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
                playerShadowPass.begin(cmdBuffer, VkClearValue.calloc(1, stack).depthStencil(d -> d.depth(1.0f).stencil(0)), imageIndex);
                submitPlayerShadow(renderSystem, cmdBuffer, imageIndex);
                playerShadowPass.end(cmdBuffer);

                cmdBuffer.setViewport(0, viewport);
                cmdBuffer.setScissor(0, scissor);

                VkClearValue.Buffer gClearValues = VkClearValue.calloc(4, stack);
                gClearValues.get(0).color().float32(0, 0).float32(1, 0).float32(2, 0).float32(3, 0);
                gClearValues.get(1).color().float32(0, 0).float32(1, 0).float32(2, 0).float32(3, 0);
                gClearValues.get(2).color().float32(0, 0).float32(1, 0).float32(2, 0).float32(3, 0);
                gClearValues.get(3).depthStencil().depth(1.0f).stencil(0);

                gbuffer.beginSubmitPass(cmdBuffer, gClearValues, imageIndex);

                submitDeferred(renderSystem, cmdBuffer, imageIndex);

                gbuffer.endSubmitPass(cmdBuffer);

                VkClearValue.Buffer clearValues = VkClearValue.calloc(2, stack);
                clearValues.get(0).color().float32(0, 0f).float32(1, 0f).float32(2, 0f).float32(3, 1);
                clearValues.get(1).depthStencil().depth(1.0f).stencil(0);

                gbuffer.transitionImagesForShading(cmdBuffer, imageIndex);
                computeSSAO(cmdBuffer, imageIndex);

                mainRenderPass.begin(cmdBuffer, clearValues, imageIndex);

                submitShading(renderSystem, cmdBuffer, deltaTime, imageIndex);

                mainRenderPass.end(cmdBuffer);

                gbuffer.transitionImagesForSubmit(cmdBuffer, imageIndex);

                PlortImageMemoryBarrier[] color = new PlortImageMemoryBarrier[]{
                        new PlortImageMemoryBarrier(
                                VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT,
                                PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL,
                                VK_QUEUE_FAMILY_IGNORED, VK_QUEUE_FAMILY_IGNORED,
                                mainColorTextures[imageIndex], mainColorTextures[imageIndex].entireResourceRange()
                        )
                };
                PlortImageMemoryBarrier[] depth = new PlortImageMemoryBarrier[]{
                        new PlortImageMemoryBarrier(
                                VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT,
                                PlortImage.Layout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL,
                                VK_QUEUE_FAMILY_IGNORED, VK_QUEUE_FAMILY_IGNORED,
                                gbuffer.depth(imageIndex), gbuffer.depth(imageIndex).entireResourceRange()
                        )
                };

                cmdBuffer.pipelineBarrier(stack, PipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT, PipelineStage.FRAGMENT_SHADER_BIT | PipelineStage.COMPUTE_SHADER_BIT, 0, null, null, color);
                cmdBuffer.pipelineBarrier(stack, PipelineStage.LATE_FRAGMENT_TESTS_BIT, PipelineStage.FRAGMENT_SHADER_BIT | PipelineStage.COMPUTE_SHADER_BIT, 0, null, null, depth);

                postRenderPass.begin(cmdBuffer, clearValues, imageIndex);

                try (MappedMemory mem = postDrawDataBuffer.map()) {
                    mem.putLong(playerController.viewBuffer(imageIndex).deviceAddress());
                    mem.putLong(metaBuffer.deviceAddress());
                }
                postPipeline.bind(cmdBuffer, PipelineBindPoint.GRAPHICS);
                cmdBuffer.bindDescriptorSets(PipelineBindPoint.GRAPHICS, postPipelineLayout, 0, stack.longs(postDescriptors.descriptorSet(imageIndex, 0)), null);
                cmdBuffer.drawMeshTasksEXT(1,1,1);

                dynamicTextBuffers.replace(imageIndex, atlas.buildTextBuffer(List.of(
                        new Text(String.format("Frame Time: %.3fms FPS: %.3f AABBs: %d", deltaTime * 1000f, 1 / deltaTime, renderSystem.aabbCount()), Colors.red(), new Vector2f(0, 64), 0.5f)
                )));

                textRenderer.renderTextBuffer(cmdBuffer, dynamicTextBuffers.get(imageIndex), imageIndex);
                textRenderer.renderTextBuffer(cmdBuffer, staticTextBuffer, imageIndex);

                postRenderPass.end(cmdBuffer);

                color[0] =
                        new PlortImageMemoryBarrier(
                                VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                                PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, PlortImage.Layout.COLOR_ATTACHMENT_OPTIMAL,
                                VK_QUEUE_FAMILY_IGNORED, VK_QUEUE_FAMILY_IGNORED,
                                mainColorTextures[imageIndex], mainColorTextures[imageIndex].entireResourceRange()
                        );
                depth[0] =
                        new PlortImageMemoryBarrier(
                                VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                                PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, PlortImage.Layout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                                VK_QUEUE_FAMILY_IGNORED, VK_QUEUE_FAMILY_IGNORED,
                                gbuffer.depth(imageIndex), gbuffer.depth(imageIndex).entireResourceRange()
                        );

                cmdBuffer.pipelineBarrier(stack, PipelineStage.FRAGMENT_SHADER_BIT, PipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT, 0, null, null, color);
                cmdBuffer.pipelineBarrier(stack, PipelineStage.FRAGMENT_SHADER_BIT, PipelineStage.LATE_FRAGMENT_TESTS_BIT, 0, null, null, depth);
            }
        }

        endFrame(imageIndex);

        currentFrameModInFlight = (currentFrameModInFlight + 1) % context.swapchain().imageCount();
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
        try {shadowInfoBuffers.close();} catch (Exception _) {}
        identityTransformBuffer.close();

        for (PlortImage shadowMap : sceneShadowMaps) if (shadowMap != null) shadowMap.close();
        for (PlortImage shadowMap : playerShadowMaps) if (shadowMap != null) shadowMap.close();
        shadowSampler.close();
        sceneShadowPass.close();
        playerShadowPass.close();

        for (PlortImage target : ssaoTargetImages) if (target != null) target.close();
        ssaoSampler.close();
        ssaoPipeline.close();
        ssaoPipelineLayout.close();
        ssaoLayout.close();
        ssaoDescriptors.close();
        ssaoModule.close();

        aabbPipeline.close();
        aabbPipelineLayout.close();
        aabbLayout.close();
        aabbDescriptors.close();
        aabbModule.close();

        noiseTexture.close();

        gbuffer.close();

        try { dynamicTextBuffers.close(); } catch (Exception _) {} // note: this doesn't actually throw an exception because text buffers do not create exceptions when closing.
        staticTextBuffer.close();

        atlas.close();
        textRenderer.close();

        mainRenderPass.close();
        for (PlortImage colorImage : mainColorTextures) if (colorImage != null) colorImage.close();

        postDrawDataBuffer.close();
        postSampler.close();
        postPipeline.close();
        postPipelineLayout.close();
        postLayout.close();
        postDescriptors.close();
        postModule.close();
        postRenderPass.close();
        for (long view : swapchainViews) if (view != 0) PlortImage.destroyView(context.device(), view);
    }
}
