package net.flamgop.borked.renderer.text;

import net.flamgop.borked.math.Matrix4f;
import net.flamgop.borked.renderer.PlortCommandBuffer;
import net.flamgop.borked.renderer.descriptor.*;
import net.flamgop.borked.renderer.PlortDevice;
import net.flamgop.borked.renderer.image.PlortImage;
import net.flamgop.borked.renderer.material.PlortTexture;
import net.flamgop.borked.renderer.renderpass.PlortRenderPass;
import net.flamgop.borked.renderer.memory.TrackedCloseable;
import net.flamgop.borked.renderer.memory.PlortBuffer;
import net.flamgop.borked.renderer.pipeline.*;
import net.flamgop.borked.renderer.swapchain.PlortSwapchain;
import net.flamgop.borked.renderer.util.ResourceHelper;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.List;

public class TextRenderer extends TrackedCloseable {

    private static final int STRING_DATA_BYTES = Matrix4f.BYTES + Long.BYTES + Integer.BYTES + Float.BYTES;
    private static final int THREADS_PER_GROUP = 64;

    private final PlortSwapchain swapchain;
    private final PlortDevice device;
    private final PlortDescriptorSetLayout layout;
    private final PlortBufferedDescriptorSetPool pool;
    private final PlortShaderModule shaderModule;
    private final PlortPipelineLayout pipelineLayout;
    private final PlortPipeline pipeline;

    public TextRenderer(PlortDevice device, PlortSwapchain swapchain, PlortRenderPass renderPass, int maxFramesInFlight) {
        super();
        this.device = device;
        this.swapchain = swapchain;
        this.layout = new PlortDescriptorSetLayout(
                device,
                new PlortDescriptor(PlortDescriptor.Type.STORAGE_BUFFER, 1, PlortShaderStage.Stage.FRAGMENT.bit()),
                new PlortDescriptor(PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER, 1, PlortShaderStage.Stage.FRAGMENT.bit())
        );
        this.pool = new PlortBufferedDescriptorSetPool(device, layout, 1, maxFramesInFlight);
        pool.label("Text Buffered Descriptor Pool");
        ByteBuffer textShader = ResourceHelper.loadFromResource("assets/shaders/text.spv");
        this.shaderModule = new PlortShaderModule(device, textShader);
        shaderModule.label("Text Combined");
        MemoryUtil.memFree(textShader);
        this.pipelineLayout = PlortPipelineLayout.builder(device)
                .pushConstant(new PlortPushConstant(0, STRING_DATA_BYTES, PlortShaderStage.Stage.MESH.bit()))
                .descriptorSetLayouts(layout)
                .build();
        this.pipeline = PlortPipeline
                .builder(device, renderPass)
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.MESH, shaderModule, "meshMain"))
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.FRAGMENT, shaderModule, "fragmentMain"))
                .layout(pipelineLayout)
                .blendState(PlortBlendState.alphaBlend())
                .depthStencilStateInfo(new PlortDepthStencilState(
                        false, false, CompareOp.LESS, false, false, new PlortDepthStencilState.StencilOpState(), new PlortDepthStencilState.StencilOpState(), 0, 0
                ))
                .buildGraphics();

        pipeline.label("Text Rendering");
    }

    public void switchAtlas(Atlas atlas, int frame) {
        device.writeDescriptorSets(List.of(
                new BufferDescriptorWrite(List.of(atlas.bakedGlyphData()), PlortDescriptor.Type.STORAGE_BUFFER, 0, pool.descriptorSet(frame, 0)),
                new TextureDescriptorWrite(new PlortTexture[]{atlas.texture()}, PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL, 1, pool.descriptorSet(frame, 0))
        ));
    }

    /// @implNote This can only be run ONCE per command buffer.
    public void renderTextBuffer(PlortCommandBuffer cmdBuffer, PlortBuffer buffer, int frame) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            cmdBuffer.bindPipeline(PipelineBindPoint.GRAPHICS, pipeline);

            int glyphCount = (int)(buffer.size() / Atlas.GLYPH_MESHLET_SIZE);
            Matrix4f projection = new Matrix4f().orthographic(0f, swapchain.extent().x(), 0f, swapchain.extent().y(), -1f, 1f, true);
            ByteBuffer stringData = stack.calloc(STRING_DATA_BYTES);
            projection.getToBuffer(stringData);
            stringData.putLong(4 * 4 * Float.BYTES, buffer.deviceAddress());
            stringData.putInt(4 * 4 * Float.BYTES + Long.BYTES, glyphCount);
            stringData.putFloat(4 * 4 * Float.BYTES + Long.BYTES + Integer.BYTES, 0f); // TODO: this is supposed to be an "outline thickness" parameter, but I don't know how to implement it right now
            cmdBuffer.pushConstants(pipelineLayout, PlortShaderStage.Stage.MESH.bit(), 0, stringData);
            cmdBuffer.bindDescriptorSets(PipelineBindPoint.GRAPHICS, pipelineLayout, 0, stack.longs(pool.descriptorSet(frame, 0)), null);

            int groupCount = (glyphCount + THREADS_PER_GROUP - 1) / THREADS_PER_GROUP;
            cmdBuffer.drawMeshTasksEXT(groupCount, 1, 1);
        }
    }

    @Override
    public void close() {
        pipeline.close();
        pipelineLayout.close();
        pool.close();
        layout.close();
        shaderModule.close();
        super.close();
    }
}
