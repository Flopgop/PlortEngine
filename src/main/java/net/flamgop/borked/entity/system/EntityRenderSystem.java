package net.flamgop.borked.entity.system;

import com.github.stephengold.joltjni.readonly.ConstAaBox;
import net.flamgop.borked.camera.PlayerController;
import net.flamgop.borked.entity.ComponentStore;
import net.flamgop.borked.entity.EntityManager;
import net.flamgop.borked.entity.components.PhysicsBody;
import net.flamgop.borked.entity.components.RenderInstance;
import net.flamgop.borked.entity.components.Renderable;
import net.flamgop.borked.entity.components.Transform;
import net.flamgop.borked.math.Matrix4f;
import net.flamgop.borked.math.Vector3f;
import net.flamgop.borked.model.PlortModel;
import net.flamgop.borked.renderer.PlortCommandBuffer;
import net.flamgop.borked.renderer.PlortRenderContext;
import net.flamgop.borked.renderer.descriptor.PlortBufferedDescriptorSetPool;
import net.flamgop.borked.renderer.descriptor.PlortDescriptor;
import net.flamgop.borked.renderer.descriptor.PlortDescriptorSetLayout;
import net.flamgop.borked.renderer.memory.*;
import net.flamgop.borked.renderer.pipeline.*;
import net.flamgop.borked.renderer.renderpass.PlortRenderPass;
import net.flamgop.borked.renderer.util.ResourceHelper;
import net.flamgop.borked.resource.ResourceIdentifier;
import net.flamgop.borked.resource.ResourceManager;
import net.flamgop.borked.util.ECSUtil;
import net.flamgop.borked.util.ShaderHelper;
import net.flamgop.borked.world.SceneData;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.nio.ByteBuffer;
import java.util.Collection;

public class EntityRenderSystem implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(EntityRenderSystem.class);
    private static final long AABB_SIZE = 2 * Vector3f.BYTES + 2 * Float.BYTES;
    private static final boolean DRAW_PLAYER_AABB = true;

    private final PlortShaderModule meshModule;
    private final PlortDescriptorSetLayout meshLayout;
    private final PlortBufferedDescriptorSetPool meshDescriptors;
    private final PlortPipelineLayout meshPipelineLayout;
    private final PlortPipeline meshPipeline;

    private final PlortShaderModule shadowModule;
    private final PlortPipelineLayout shadowPipelineLayout;
    private final PlortPipeline shadowPipeline;

    private final PlayerController playerController;
    private final SceneData sceneData;

    private final PlortAllocator allocator;
    private final BufferedObject<PlortBuffer> aabbBuffer;
    private long aabbCount;

    public EntityRenderSystem(ResourceManager resourceManager, PlortRenderContext context, PlayerController playerController, PlortRenderPass renderPass, PlortRenderPass shadowPass) {
        this.allocator = context.allocator();
        this.playerController = playerController;

        this.meshModule = ShaderHelper.load(context.device(), resourceManager, ResourceIdentifier.withDefaultNamespace("shaders/mesh.spv"));
        meshModule.label("Mesh");

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
        this.meshPipeline = PlortPipeline.builder(context.device(), renderPass)
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.MESH, meshModule, "meshMain"))
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.FRAGMENT, meshModule, "fragmentMain"))
                .layout(meshPipelineLayout)
                .blendState(PlortBlendState.disabled())
                .blendState(PlortBlendState.disabled())
                .blendState(PlortBlendState.disabled())
                .buildGraphics();

        ByteBuffer shadowCode = ResourceHelper.loadFromResource("assets/borked/shaders/shadow.spv");
        this.shadowModule = new PlortShaderModule(context.device(), shadowCode);
        shadowModule.label("Shadow");
        MemoryUtil.memFree(shadowCode);

        this.shadowPipelineLayout = PlortPipelineLayout.builder(context.device())
                .descriptorSetLayouts(meshLayout)
                .pushConstant(new PlortPushConstant(0, 4 * Long.BYTES, PlortShaderStage.Stage.ALL.bit()))
                .build();
        this.shadowPipeline = PlortPipeline.builder(context.device(), shadowPass)
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.MESH, shadowModule, "meshMain"))
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.FRAGMENT, shadowModule, "fragmentMain"))
                .layout(shadowPipelineLayout)
                .depthStencilStateInfo(new PlortDepthStencilState(true, true, CompareOp.LESS, false, false, new PlortDepthStencilState.StencilOpState(), new PlortDepthStencilState.StencilOpState(), 0f, 1f))
                .buildGraphics();

        this.sceneData = new SceneData(context.allocator());
        this.aabbBuffer = new BufferedObject<>(PlortBuffer.class, context.swapchain().imageCount(), _ -> null);
    }

    public void recreateAABBBuffer(EntityManager entityManager, int frameMod) {
        ComponentStore<PhysicsBody> bodyStore = entityManager.store(PhysicsBody.class);
        Collection<Integer> bodies = bodyStore.entities();
        aabbCount = 0;
        for (int e : bodies) {
            PhysicsBody body = bodyStore.get(e);
            aabbCount += body.bodies().size();
        }
        if (DRAW_PLAYER_AABB) aabbCount += 1;

        if (aabbCount <= 0) {
            LOGGER.debug("No AABBs to build in buffer.");
            return;
        }
        PlortBuffer buffer = new PlortBuffer(aabbCount * AABB_SIZE, BufferUsage.STORAGE_BUFFER_BIT, allocator);
        try (MappedMemory mem = buffer.map()) {
            Vector3f temp = new Vector3f();
            for (int e : bodies) {
                PhysicsBody body = bodyStore.get(e);
                body.bodies().forEach(a -> {
                    ConstAaBox aabb = a.getWorldSpaceBounds();
                    mem.putVector3f(temp.setFrom(aabb.getMin()));
                    mem.putFloat(0);
                    mem.putVector3f(temp.setFrom(aabb.getMax()));
                    mem.putFloat(1);
                });
            }
            if (DRAW_PLAYER_AABB) {
                mem.putVector3f(temp.setFrom(playerController.aabb().getMin()));
                mem.putFloat(0);
                mem.putVector3f(temp.setFrom(playerController.aabb().getMax()));
                mem.putFloat(0);
            }
        }
        aabbBuffer.replace(frameMod, buffer);
    }

    public PlortBuffer aabbBuffer(int frame) {
        return aabbBuffer.get(frame);
    }

    public long aabbCount() {
        return aabbCount;
    }

    public SceneData sceneData() {
        return sceneData;
    }

    public void render(PlortCommandBuffer cmdBuffer, EntityManager entityManager, int frameInFlight, boolean shadow) {
        if (sceneData.dirty()) {
            sceneData.upload();
        }

        if (!shadow) meshPipeline.bind(cmdBuffer, PipelineBindPoint.GRAPHICS);
        else shadowPipeline.bind(cmdBuffer, PipelineBindPoint.GRAPHICS);

        ComponentStore<Transform> transforms = entityManager.store(Transform.class);
        ComponentStore<Renderable> renderables = entityManager.store(Renderable.class);
        ComponentStore<RenderInstance> instances = entityManager.store(RenderInstance.class);

        ComponentStore<?> smaller = ECSUtil.smallest(transforms, renderables, instances);
        for (int e : smaller.entities()) {
            if (!transforms.has(e) || !renderables.has(e) || !instances.has(e)) continue;

            Transform transform = transforms.get(e);
            Renderable renderable = renderables.get(e);
            RenderInstance instance = instances.get(e);

            if (transform.dirty()) upload(instance, transform);
            if (!shadow) submit(cmdBuffer, renderable.model(), instance, frameInFlight);
            else submitShadow(cmdBuffer, renderable.model(), instance, frameInFlight);
        }
    }

    public void renderPlayer(PlortCommandBuffer cmdBuffer, int frameInFlight, boolean shadow) {
        if (shadow) shadowPipeline.bind(cmdBuffer, PipelineBindPoint.GRAPHICS);
        else meshPipeline.bind(cmdBuffer, PipelineBindPoint.GRAPHICS);
        playerController.submit(cmdBuffer, shadow ? shadowPipelineLayout : meshPipelineLayout, frameInFlight, shadow);
    }

    private void upload(RenderInstance instance, Transform transform) {
        try (Arena arena = Arena.ofConfined()) {
            Matrix4f workingMatrix = new Matrix4f(arena);
            for (PlortBuffer buffer : instance.buffers()) {
                try (MappedMemory mem = buffer.map()) {
                    mem.putMatrix4f(transform.transform());
                    mem.putMatrix4f(workingMatrix.setFrom(transform.transform()).invert());
                }
            }
        }
    }

    private void submit(PlortCommandBuffer cmdBuffer, PlortModel model, RenderInstance instance, int frameInFlight) {
        model.submit(cmdBuffer, meshPipelineLayout, instance.buffers().get(frameInFlight), instance.descriptorSetPool(), 1, frameInFlight);
    }

    private void submitShadow(PlortCommandBuffer cmdBuffer, PlortModel model, RenderInstance instance, int frameInFlight) {
        model.submit(cmdBuffer, shadowPipelineLayout, instance.buffers().get(frameInFlight), instance.shadowDescriptorSetPool(), 1, frameInFlight);
    }

    @Override
    public void close() {
        sceneData.close();
        try { aabbBuffer.close(); } catch (Exception _) {}

        shadowPipeline.close();
        shadowPipelineLayout.close();
        shadowModule.close();

        meshPipeline.close();
        meshPipelineLayout.close();
        meshDescriptors.close();
        meshLayout.close();
        meshModule.close();
    }
}
