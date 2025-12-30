package net.flamgop.borked.renderer.pipeline;

import net.flamgop.borked.renderer.PlortDevice;
import net.flamgop.borked.renderer.descriptor.PlortDescriptorSetLayout;
import net.flamgop.borked.renderer.memory.TrackedCloseable;
import net.flamgop.borked.renderer.util.VkUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.vulkan.VK10.*;

public class PlortPipelineLayout extends TrackedCloseable {

    public static Builder builder(PlortDevice device) {
        return new Builder(device);
    }

    public static class Builder {
        private final PlortDevice device;
        private final List<PlortPushConstant> pushConstants = new ArrayList<>();
        private PlortDescriptorSetLayout[] descriptorSetLayouts = null;

        Builder(PlortDevice device) {
            this.device = device;
        }

        public Builder pushConstant(PlortPushConstant pushConstant) {
            pushConstants.add(pushConstant);
            return this;
        }

        public Builder descriptorSetLayouts(PlortDescriptorSetLayout... layouts) {
            this.descriptorSetLayouts = layouts;
            return this;
        }

        public PlortPipelineLayout build() {
            if (descriptorSetLayouts == null) throw new NullPointerException("Pipelines require at least one descriptor set layout!");
            return new PlortPipelineLayout(device, pushConstants, descriptorSetLayouts);
        }
    }

    private final PlortDevice device;
    private final long handle;

    public PlortPipelineLayout(PlortDevice device, List<PlortPushConstant> pushConstants, PlortDescriptorSetLayout[] descriptorSetLayouts) {
        super();
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPushConstantRange.Buffer pushConstantBuffer = VkPushConstantRange.calloc(pushConstants.size(), stack);
            for (int i = 0; i < pushConstants.size(); i++) {
                PlortPushConstant pushConstant = pushConstants.get(i);
                pushConstantBuffer.get(i)
                        .stageFlags(pushConstant.stageFlags())
                        .offset(pushConstant.offset())
                        .size(pushConstant.size());
            }

            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pSetLayouts(stack.longs(Arrays.stream(descriptorSetLayouts).mapToLong(PlortDescriptorSetLayout::handle).toArray()))
                    .pPushConstantRanges(!pushConstants.isEmpty() ? pushConstantBuffer : null);

            LongBuffer pOut = stack.callocLong(1);
            VkUtil.check(vkCreatePipelineLayout(device.handle(), pipelineLayoutInfo, null, pOut));
            this.handle = pOut.get(0);
        }
    }

    public long handle() {
        return handle;
    }

    @Override
    public void close() {
        super.close();
        vkDestroyPipelineLayout(device.handle(), handle, null);
    }
}
