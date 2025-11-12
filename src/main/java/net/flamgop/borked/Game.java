package net.flamgop.borked;

import net.flamgop.borked.engine.PlortBufferedDescriptorSetPool;
import net.flamgop.borked.engine.PlortEngine;
import net.flamgop.borked.engine.model.PlortMesh;
import net.flamgop.borked.engine.PlortRenderPass;
import net.flamgop.borked.engine.image.*;
import net.flamgop.borked.engine.memory.*;
import net.flamgop.borked.engine.pipeline.*;
import net.flamgop.borked.engine.text.Atlas;
import net.flamgop.borked.engine.text.Text;
import net.flamgop.borked.engine.text.TextAlign;
import net.flamgop.borked.engine.text.TextRenderer;
import net.flamgop.borked.engine.util.VkUtil;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.par.ParShapes;
import org.lwjgl.util.par.ParShapesMesh;
import org.lwjgl.vulkan.*;
import org.renderdoc.api.RenderDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_SUBPASS_CONTENTS_INLINE;
import static org.lwjgl.vulkan.VK10.vkCmdBeginRenderPass;
import static org.lwjgl.vulkan.VK10.vkCmdBindDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCmdBindPipeline;
import static org.lwjgl.vulkan.VK10.vkCmdEndRenderPass;
import static org.lwjgl.vulkan.VK10.vkCmdSetScissor;
import static org.lwjgl.vulkan.VK10.vkEndCommandBuffer;

public class Game {

    private static final Logger LOGGER = LoggerFactory.getLogger(Game.class);

    private final PlortEngine engine;

    private final PlortRenderPass mainRenderPass;

    private final TextRenderer textRenderer;
    private final Atlas atlas;

    private final PlortShaderModule meshShaderModule;
    private final PlortBufferedDescriptorSetPool meshDescriptorSetPool;
    private final PlortPipeline meshPipeline;

    private final PlortMesh mesh0, mesh1;
    private final PlortBuffer transform0, transform1;

    private final PlortBuffer viewBuffer;

    private final PlortImage testImage, equirectangularImage;
    private final PlortSampler imageSampler;

    private final BufferedObject<PlortBuffer> textBuffers;

    public Game() {
        RenderDoc rd = RenderDoc.load(RenderDoc.KnownVersion.API_1_6_0);

        rd.setCaptureOptionU32(RenderDoc.CaptureOption.CAPTURE_ALL_CMD_LISTS, 1);
        rd.setCaptureOptionU32(RenderDoc.CaptureOption.CAPTURE_CALLSTACKS, 1);
        rd.setCaptureOptionU32(RenderDoc.CaptureOption.REF_ALL_RESOURCES, 1);
        rd.setCaptureOptionU32(RenderDoc.CaptureOption.API_VALIDATION, 1);
        rd.setCaptureOptionU32(RenderDoc.CaptureOption.HOOK_INTO_CHILDREN, 1);
        rd.setCaptureOptionU32(RenderDoc.CaptureOption.VERIFY_BUFFER_ACCESS, 1);
        rd.setCaptureOptionU32(RenderDoc.CaptureOption.DEBUG_OUTPUT_MUTE, 0);

        this.engine = new PlortEngine("Game", VkUtil.makeApiVersion(1,0,0,0));
        engine.onSwapchainInvalidate(this::onSwapchainInvalidate);

        engine.swapchain().label("Main");

        this.mainRenderPass = new PlortRenderPass(engine.device(), engine.swapchain(), engine.allocator());
        mainRenderPass.label("Main");

        this.textRenderer = new TextRenderer(engine.device(), engine.swapchain(), mainRenderPass, engine.swapchain().imageCount());
        this.atlas = new Atlas(engine.device(), engine.allocator(), engine.commandPool(), "nunito");

        this.meshShaderModule = PlortShaderModule.fromResource(engine.device(), "mesh.spv");
        meshShaderModule.label("Mesh Combined");
        PlortDescriptorSet set = new PlortDescriptorSet(
                new PlortDescriptor(PlortDescriptor.Type.STORAGE_BUFFER, 1, PlortShaderStage.Stage.MESH.bit()), // vertex_data
                new PlortDescriptor(PlortDescriptor.Type.STORAGE_BUFFER, 1, PlortShaderStage.Stage.MESH.bit()), // meshlet_data
                new PlortDescriptor(PlortDescriptor.Type.STORAGE_BUFFER, 1, PlortShaderStage.Stage.TASK.bit()), // bounds_data
                new PlortDescriptor(PlortDescriptor.Type.STORAGE_BUFFER, 1, PlortShaderStage.Stage.TASK.bit() | PlortShaderStage.Stage.MESH.bit()), // instance_data
                new PlortDescriptor(PlortDescriptor.Type.UNIFORM_BUFFER, 1, PlortShaderStage.Stage.TASK.bit() | PlortShaderStage.Stage.MESH.bit()), // view_data
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit()) // albedo
        );
        this.meshDescriptorSetPool = new PlortBufferedDescriptorSetPool(engine.device(), List.of(set, set), 3);
        meshDescriptorSetPool.label("Mesh");

        this.meshPipeline = PlortPipeline.builder(engine.device(), mainRenderPass)
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.TASK, meshShaderModule, "taskMain"))
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.MESH, meshShaderModule, "meshMain"))
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.FRAGMENT, meshShaderModule, "fragmentMain"))
                .descriptorSetPool(meshDescriptorSetPool.pool())
                .blendState(PlortBlendState.disabled())
                .build();
        meshPipeline.label("Mesh");

        ParShapesMesh parMesh0 = ParShapes.par_shapes_create_plane(16, 16);
        if (parMesh0 == null) throw new RuntimeException("shape");
        ParShapes.par_shapes_rotate(parMesh0, (float) Math.toRadians(-90), new float[]{1,0,0});
        ParShapes.par_shapes_translate(parMesh0, -0.5f, 0, 0.5f);
        ParShapes.par_shapes_scale(parMesh0, 10, 10, 10);
        mesh0 = new PlortMesh(engine.allocator(), parMesh0);
        ParShapes.par_shapes_free_mesh(parMesh0);

        mesh1 = new PlortMesh(engine.allocator(), "test.glb");

        transform0 = new PlortBuffer(2 * 4 * 4 * Float.BYTES, BufferUsage.STORAGE_BUFFER_BIT, engine.allocator());
        transform0.label("Transform 0");
        transform1 = new PlortBuffer(2 * 4 * 4 * Float.BYTES, BufferUsage.STORAGE_BUFFER_BIT, engine.allocator());
        transform1.label("Transform 1");

        try (MappedMemory mem = transform0.map()) {
            Matrix4f transform = new Matrix4f().translate(0, 0, 0);
            mem.putMatrix4f(transform);
            mem.putMatrix4f(transform.invert(new Matrix4f()));
        }

        try (MappedMemory mem = transform1.map()) {
            Matrix4f transform = new Matrix4f().translate(0,3,0);
            mem.putMatrix4f(transform);
            mem.putMatrix4f(transform.invert(new Matrix4f()));
        }

        viewBuffer = new PlortBuffer(4 * 4 * Float.BYTES + 4 * Float.BYTES, BufferUsage.UNIFORM_BUFFER_BIT, engine.allocator());
        viewBuffer.label("View");

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer testDims = stack.callocInt(3);
            PlortBuffer testBuffer = loadImageIntoBuffer("image.png", testDims, 4);

            IntBuffer equiDims = stack.callocInt(3);
            PlortBuffer equiBuffer = loadImageIntoBuffer("equirectangular.jpg", equiDims, 4);

            this.testImage = new PlortImage(
                    engine.device(),
                    engine.allocator(),
                    PlortImage.Type.TYPE_2D,
                    new Vector3i(testDims.get(0), testDims.get(1), 1),
                    1, 1,
                    Format.R8G8B8A8_UNORM,
                    PlortImage.Layout.PREINITIALIZED,
                    ImageUsage.TRANSFER_DST_BIT | ImageUsage.SAMPLED_BIT,
                    1,
                    SharingMode.EXCLUSIVE,
                    MemoryUsage.GPU_ONLY,
                    PlortImage.ViewType.TYPE_2D,
                    AspectMask.COLOR_BIT
            );
            testImage.label("Test");

            this.equirectangularImage = new PlortImage(
                    engine.device(),
                    engine.allocator(),
                    PlortImage.Type.TYPE_2D,
                    new Vector3i(equiDims.get(0), equiDims.get(1), 1),
                    1, 1,
                    Format.R8G8B8A8_UNORM,
                    PlortImage.Layout.PREINITIALIZED,
                    ImageUsage.TRANSFER_DST_BIT | ImageUsage.SAMPLED_BIT,
                    1,
                    SharingMode.EXCLUSIVE,
                    MemoryUsage.GPU_ONLY,
                    PlortImage.ViewType.TYPE_2D,
                    AspectMask.COLOR_BIT
            );
            equirectangularImage.label("Equirectangular");

            engine.commandPool().transientSubmit(engine.device().graphicsQueue(), 0, (cmd) -> {
                VkUtil.copyBufferToImage(cmd, testBuffer, testImage, testDims.get(0), testDims.get(1));
                VkUtil.copyBufferToImage(cmd, equiBuffer, equirectangularImage, equiDims.get(0), equiDims.get(1));
            });

            testBuffer.close();
            equiBuffer.close();
        }

        imageSampler = new PlortSampler(
                engine.device(),
                PlortSampler.Filter.LINEAR, PlortSampler.Filter.LINEAR,
                PlortSampler.AddressMode.REPEAT, PlortSampler.AddressMode.REPEAT, PlortSampler.AddressMode.REPEAT
        );
        imageSampler.label("Default");

        textBuffers = new BufferedObject<>(PlortBuffer.class, engine.swapchain().imageCount(), () -> atlas.buildTextBuffer(List.of(
                new Text(String.format("FPS: %.3f", 0f), Colors.red(), new Vector2f(0, 64), 0.5f),
                new Text("Here's another line of even cooler text", Colors.blue(), new Vector2f(0, 64 + atlas.lineHeight() * 0.5f), 0.5f),
                new Text("And another line of yet cooler text", Colors.green(), new Vector2f(0, 64 + 2 * atlas.lineHeight() * 0.5f), 0.5f),
                new Text(
                        "This line is in the center of the screen!!",
                        Colors.magenta(),
                        new Vector2f(1280 / 2f - atlas.stringWidth("This line is in the center of the screen!!", 10f) / 2f, 720 / 2f),
                        0.5f
                )
        )));
    }

    @SuppressWarnings("SameParameterValue")
    private PlortBuffer loadImageIntoBuffer(String imageName, IntBuffer dims, int desiredChannels) {
        ByteBuffer imageData = STBImage.stbi_load(imageName, dims.slice(0,1), dims.slice(1,1), dims.slice(2,1), desiredChannels);
        if (imageData == null) throw new RuntimeException(imageName);

        PlortBuffer imageBuffer = new PlortBuffer(imageData.capacity(), BufferUsage.TRANSFER_SRC_BIT, engine.allocator());
        try (MappedMemory mem = imageBuffer.map()) {
            mem.copyEntireBuffer(imageData);
        }

        STBImage.stbi_image_free(imageData);
        return imageBuffer;
    }

    public void start() {
        long prevFrameStart = System.nanoTime();
        double deltaTime;

        for (int i = 0; i < engine.swapchain().imageCount(); i++) {
            updateBuffers(i);
        }

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

            try (MappedMemory mem = viewBuffer.map()) {
                final int distance = 5;
                double time = GLFW.glfwGetTime();
                Vector3f cameraPos = new Vector3f((float) (distance * Math.cos(time)), distance, (float) (distance * Math.sin(time)));
                Matrix4f viewProj = new Matrix4f()
                        .perspective(
                                (float) Math.toRadians(90f),
                                (float) engine.swapchain().extent().x() / engine.swapchain().extent().y(),
                                0.1f, 100f,
                                true
                        ).mul(new Matrix4f().lookAt(
                                cameraPos,
                                new Vector3f(0, 0, 0),
                                new Vector3f(0, 1, 0)
                        ));


                for (float f : viewProj.get(new float[16])) mem.putFloat(f);
                mem.putFloat(cameraPos.x());
                mem.putFloat(cameraPos.y());
                mem.putFloat(cameraPos.z());
                mem.putFloat(0);
            }

            frame(deltaTime, currentFrameModInFlight, imageIndex);

            endFrame(currentFrameModInFlight, imageIndex);

            currentFrameModInFlight = (currentFrameModInFlight + 1) % engine.swapchain().imageCount();
            engine.window().pollEvents();
        }
        cleanup();
    }

    @SuppressWarnings("resource")
    private void updateBuffers(int imageIndex) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(6, stack);

            mesh0.updateDescriptors(stack, meshDescriptorSetPool.descriptorSet(imageIndex, 0), writes, 0);

            VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
            transform0.info(bufferInfo.get(0));

            writes.get(3)
                    .sType$Default()
                    .dstSet(meshDescriptorSetPool.descriptorSet(imageIndex, 0))
                    .dstBinding(3)
                    .descriptorType(PlortDescriptor.Type.STORAGE_BUFFER.qualifier())
                    .descriptorCount(1)
                    .pBufferInfo(bufferInfo);

            VkDescriptorBufferInfo.Buffer uniformInfo = VkDescriptorBufferInfo.calloc(1, stack);
            viewBuffer.info(uniformInfo.get(0));

            writes.get(4)
                    .sType$Default()
                    .dstSet(meshDescriptorSetPool.descriptorSet(imageIndex, 0))
                    .dstBinding(4)
                    .descriptorType(PlortDescriptor.Type.UNIFORM_BUFFER.qualifier())
                    .descriptorCount(1)
                    .pBufferInfo(uniformInfo);

            VkDescriptorImageInfo.Buffer imageInfoBuf = VkDescriptorImageInfo.calloc(1, stack)
                    .imageLayout(PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL.qualifier());

            testImage.info(imageInfoBuf.get(0));
            imageSampler.info(imageInfoBuf.get(0));

            writes.get(5)
                    .sType$Default()
                    .dstSet(meshDescriptorSetPool.descriptorSet(imageIndex, 0))
                    .dstBinding(5)
                    .descriptorType(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER.qualifier())
                    .descriptorCount(1)
                    .pImageInfo(imageInfoBuf);

            engine.device().updateDescriptorSets(writes, null);

            mesh1.updateDescriptors(stack, meshDescriptorSetPool.descriptorSet(imageIndex, 1), writes, 0);

            transform1.info(bufferInfo.get(0));

            writes.get(3)
                    .sType$Default()
                    .dstSet(meshDescriptorSetPool.descriptorSet(imageIndex, 1))
                    .dstBinding(3)
                    .descriptorType(PlortDescriptor.Type.STORAGE_BUFFER.qualifier())
                    .descriptorCount(1)
                    .pBufferInfo(bufferInfo);

            writes.get(4)
                    .dstSet(meshDescriptorSetPool.descriptorSet(imageIndex, 1));

            equirectangularImage.info(imageInfoBuf.get(0));
            writes.get(5)
                    .dstSet(meshDescriptorSetPool.descriptorSet(imageIndex, 1));

            engine.device().updateDescriptorSets(writes, null);
        }
    }

    @SuppressWarnings("resource")
    private void frame(double deltaTime, int currentFrameModInFlight, int imageIndex) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkClearValue.Buffer clearValues = VkClearValue.calloc(2, stack);
            clearValues.get(0).color().float32(0, 0.0595f).float32(1, 0.0595f).float32(2, 0.0595f).float32(3, 1);
            clearValues.get(1).depthStencil().depth(1.0f).stencil(0);
            VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(mainRenderPass.handle())
                    .framebuffer(mainRenderPass.framebuffer(imageIndex))
                    .renderArea(a -> a.offset(o -> o.set(0, 0)).extent(e -> e.set(engine.swapchain().extent().x(), engine.swapchain().extent().y())))
                    .pClearValues(clearValues);

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

            VkCommandBuffer cmdBuffer = engine.drawBuffer(imageIndex);

            vkBeginCommandBuffer(cmdBuffer, beginInfo);

            vkCmdSetViewport(cmdBuffer, 0, viewport);
            vkCmdSetScissor(cmdBuffer, 0, scissor);

            vkCmdBeginRenderPass(cmdBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);

            vkCmdBindPipeline(cmdBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, meshPipeline.handle());

            vkCmdBindDescriptorSets(cmdBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, meshPipeline.layout(), 0, stack.longs(meshDescriptorSetPool.descriptorSet(imageIndex, 0)), null);
            mesh0.recordDrawCommand(cmdBuffer);

            vkCmdBindDescriptorSets(cmdBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, meshPipeline.layout(), 0, stack.longs(meshDescriptorSetPool.descriptorSet(imageIndex, 1)), null);
            mesh1.recordDrawCommand(cmdBuffer);

            textBuffers.replace(imageIndex, atlas.buildTextBuffer(List.of(
                    new Text(String.format("Frame Time: %.3fms FPS: %.3f", deltaTime * 1000f, 1 / deltaTime), Colors.red(), new Vector2f(0, 64), 0.5f),
                    new Text("Here's another line of even cooler text", Colors.blue(), new Vector2f(0, 64 + atlas.lineHeight() * 0.5f), 0.5f),
                    new Text("And another line of yet cooler text", Colors.green(), new Vector2f(0, 64 + 2 * atlas.lineHeight() * 0.5f), 0.5f),
                    new Text(
                            "This line is in the center of the screen!!\nAnd this second line is in the same text block!!",
                            Colors.magenta(),
                            new Vector2f(
                                    engine.swapchain().extent().x() / 2f,
                                    engine.swapchain().extent().y() / 2f - atlas.stringHeight("This line is in the center of the screen!!\nAnd this second line is in the same text block!!", 0.5f) / 2f
                            ),
                            0.5f,
                            TextAlign.CENTER,
                            0.5f
                    )
            )));

            textRenderer.renderTextBuffer(cmdBuffer, atlas, textBuffers.get(imageIndex), imageIndex);

            vkCmdEndRenderPass(cmdBuffer);

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
        this.mainRenderPass.recreate();
    }

    public void cleanup() {
        engine.device().waitIdle();

        try {
            textBuffers.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        imageSampler.close();
        testImage.close(); equirectangularImage.close();

        viewBuffer.close();

        transform0.close(); transform1.close();
        mesh0.close(); mesh1.close();

        meshPipeline.close();
        meshDescriptorSetPool.close();
        meshShaderModule.close();

        atlas.close();
        textRenderer.close();

        mainRenderPass.close();
        engine.close(); // ALWAYS LAST!!!!
    }
}
