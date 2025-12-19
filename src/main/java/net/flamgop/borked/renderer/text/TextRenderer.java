package net.flamgop.borked.renderer.text;

import net.flamgop.borked.math.Matrix4f;
import net.flamgop.borked.renderer.descriptor.PlortBufferedDescriptorSetPool;
import net.flamgop.borked.renderer.PlortDevice;
import net.flamgop.borked.renderer.descriptor.PlortDescriptor;
import net.flamgop.borked.renderer.descriptor.PlortDescriptorSetLayout;
import net.flamgop.borked.renderer.renderpass.PlortRenderPass;
import net.flamgop.borked.renderer.memory.TrackedCloseable;
import net.flamgop.borked.renderer.memory.PlortBuffer;
import net.flamgop.borked.renderer.pipeline.*;
import net.flamgop.borked.renderer.swapchain.PlortSwapchain;
import net.flamgop.borked.renderer.util.ResourceHelper;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.EXTMeshShader.vkCmdDrawMeshTasksEXT;
import static org.lwjgl.vulkan.VK10.*;

@SuppressWarnings("resource")
public class TextRenderer extends TrackedCloseable {

    private static final int STRING_DATA_BYTES = 4 * 4 * Float.BYTES + Long.BYTES + Integer.BYTES + Float.BYTES;
    private static final int THREADS_PER_GROUP = 64;

    private final PlortSwapchain swapchain;
    private final PlortDevice device;
    private final PlortDescriptorSetLayout layout;
    private final PlortBufferedDescriptorSetPool pool;
    private final PlortShaderModule shaderModule;
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
        this.pipeline = PlortPipeline
                .builder(device, renderPass)
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.MESH, shaderModule, "meshMain"))
                .shaderStage(new PlortShaderStage(PlortShaderStage.Stage.FRAGMENT, shaderModule, "fragmentMain"))
                .pushConstant(new PlortPushConstant(0, STRING_DATA_BYTES, PlortShaderStage.Stage.MESH.bit()))
                .descriptorSetLayouts(layout)
                .blendState(PlortBlendState.alphaBlend())
                .depthStencilStateInfo(new PlortDepthStencilState(
                        false, false, CompareOp.LESS, false, false, new PlortDepthStencilState.StencilOpState(), new PlortDepthStencilState.StencilOpState(), 0, 0
                ))
                .buildGraphics();

        pipeline.label("Text Rendering");
    }

    public void switchAtlas(Atlas atlas, int frame) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(2, stack);

            VkDescriptorBufferInfo.Buffer bufferInfos = VkDescriptorBufferInfo.calloc(1, stack);
            bufferInfos.get(0)
                    .offset(0)
                    .buffer(atlas.bakedGlyphData().handle())
                    .range(atlas.bakedGlyphData().size());

            writes.get(0)
                    .sType$Default()
                    .dstSet(pool.descriptorSet(frame, 0))
                    .dstBinding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .pBufferInfo(bufferInfos.slice(0, 1));

            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                    .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .imageView(atlas.view())
                    .sampler(atlas.sampler());

            writes.get(1)
                    .sType$Default()
                    .dstSet(pool.descriptorSet(frame, 0))
                    .dstBinding(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(imageInfo);

            vkUpdateDescriptorSets(device.handle(), writes, null);
        }
    }

    /// @implNote This can only be run ONCE per command buffer.
    public void renderTextBuffer(VkCommandBuffer cmdBuffer, PlortBuffer buffer, int frame) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkCmdBindPipeline(cmdBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.handle());

            int glyphCount = (int)(buffer.size() / Atlas.GLYPH_MESHLET_SIZE);
            Matrix4f projection = new Matrix4f().orthographic(0f, swapchain.extent().x(), 0f, swapchain.extent().y(), -1f, 1f, true);
            ByteBuffer stringData = stack.calloc(STRING_DATA_BYTES);
            projection.getToBuffer(stringData);
            stringData.putLong(4 * 4 * Float.BYTES, buffer.deviceAddress());
            stringData.putInt(4 * 4 * Float.BYTES + Long.BYTES, glyphCount);
            stringData.putFloat(4 * 4 * Float.BYTES + Long.BYTES + Integer.BYTES, 0f); // TODO: this is supposed to be an "outline thickness" parameter, but I don't know how to implement it right now
            vkCmdPushConstants(cmdBuffer, pipeline.layout(), PlortShaderStage.Stage.MESH.bit(), 0, stringData);
            vkCmdBindDescriptorSets(cmdBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.layout(), 0, stack.longs(pool.descriptorSet(frame, 0)), null);

            int groupCount = (glyphCount + THREADS_PER_GROUP - 1) / THREADS_PER_GROUP;
            vkCmdDrawMeshTasksEXT(cmdBuffer, groupCount, 1, 1);
        }
    }

    @Override
    public void close() {
        pipeline.close();
        pool.close();
        layout.close();
        shaderModule.close();
        super.close();
    }
}
