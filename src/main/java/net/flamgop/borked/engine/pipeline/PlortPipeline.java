package net.flamgop.borked.engine.pipeline;

import net.flamgop.borked.engine.PlortDevice;
import net.flamgop.borked.engine.PlortRenderPass;
import net.flamgop.borked.engine.memory.TrackedCloseable;
import net.flamgop.borked.engine.util.VkUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.EXTDebugUtils.vkSetDebugUtilsObjectNameEXT;
import static org.lwjgl.vulkan.VK14.*;

public class PlortPipeline extends TrackedCloseable {
    private final PlortDevice device;

    private final long handle, pipelineLayout;

    public static Builder builder(PlortDevice device, PlortRenderPass renderPass) {
        return new Builder(device, renderPass);
    }

    public static class Builder {
        private final PlortDevice device;
        private final PlortRenderPass renderPass;

        private final List<PlortShaderStage> shaderStages = new ArrayList<>();
        private final List<PlortPushConstant> pushConstants = new ArrayList<>();

        private PlortDescriptorSetPool descriptorSetPool;
        private PlortBlendState blendState = PlortBlendState.disabled();
        private PlortDepthStencilState depthState = new PlortDepthStencilState();

        Builder(PlortDevice device, PlortRenderPass renderPass) {
            this.device = device;
            this.renderPass = renderPass;
        }

        public Builder descriptorSetPool(PlortDescriptorSetPool descriptorSetPool) {
            this.descriptorSetPool = descriptorSetPool;
            return this;
        }

        public Builder shaderStage(PlortShaderStage stage) {
            shaderStages.add(stage);
            return this;
        }

        public Builder pushConstant(PlortPushConstant pushConstant) {
            pushConstants.add(pushConstant);
            return this;
        }

        public Builder blendState(PlortBlendState blendState) {
            this.blendState = blendState;
            return this;
        }

        public Builder depthStencilStateInfo(PlortDepthStencilState depthState) {
            this.depthState = depthState;
            return this;
        }

        public PlortPipeline build() {
            if (descriptorSetPool == null) throw new NullPointerException("Descriptor sets need to be set before building!");
            return new PlortPipeline(device, renderPass, shaderStages, pushConstants, descriptorSetPool, blendState, depthState);
        }
    }

    @SuppressWarnings({"resource"})
    public PlortPipeline(PlortDevice device, PlortRenderPass renderPass, List<PlortShaderStage> shaderStages, List<PlortPushConstant> pushConstants, PlortDescriptorSetPool descriptorSetPool, PlortBlendState blendState, PlortDepthStencilState depthState) {
        super();
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pOut = stack.callocLong(1);
            VkPipelineShaderStageCreateInfo.Buffer shaderStageInfos = VkPipelineShaderStageCreateInfo.calloc(shaderStages.size(), stack);
            for (int i = 0; i < shaderStages.size(); i++) {
                PlortShaderStage shaderStage = shaderStages.get(i);
                shaderStageInfos.get(i)
                        .sType$Default()
                        .stage(shaderStage.stage().bit())
                        .module(shaderStage.module().handle())
                        .pName(stack.UTF8(shaderStage.entrypointName()));
            }

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
                    .pSetLayouts(stack.longs(descriptorSetPool.layouts()))
                    .pPushConstantRanges(!pushConstants.isEmpty() ? pushConstantBuffer : null);

            VkUtil.check(vkCreatePipelineLayout(device.handle(), pipelineLayoutInfo, null, pOut));
            this.pipelineLayout = pOut.get(0);

            VkPipelineMultisampleStateCreateInfo multisampleStateInfo = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
                    .sampleShadingEnable(false)
                    .minSampleShading(1.0f)
                    .pSampleMask(null)
                    .alphaToCoverageEnable(false)
                    .alphaToOneEnable(false);

            VkPipelineRasterizationStateCreateInfo rasterizationStateInfo = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .depthClampEnable(false)
                    .rasterizerDiscardEnable(false)
                    .polygonMode(VK_POLYGON_MODE_FILL)
                    .cullMode(VK_CULL_MODE_BACK_BIT)
                    .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                    .depthBiasEnable(false)
                    .lineWidth(1.0f);

            VkPipelineDynamicStateCreateInfo dynamicStateInfo = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

            VkPipelineViewportStateCreateInfo viewportStateInfo = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .viewportCount(1)
                    .scissorCount(1);

            VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachments = VkPipelineColorBlendAttachmentState.calloc(1, stack)
                    .blendEnable(blendState.enable())
                    .srcColorBlendFactor(blendState.srcColorBlendFactor().qualifier())
                    .dstColorBlendFactor(blendState.dstColorBlendFactor().qualifier())
                    .colorBlendOp(blendState.colorBlendOp().qualifier())
                    .srcAlphaBlendFactor(blendState.srcAlphaBlendFactor().qualifier())
                    .dstAlphaBlendFactor(blendState.dstAlphaBlendFactor().qualifier())
                    .alphaBlendOp(blendState.alphaBlendOp().qualifier())
                    .colorWriteMask(blendState.colorWriteMask());

            VkPipelineColorBlendStateCreateInfo colorBlendStateInfo = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .logicOpEnable(false)
                    .blendConstants(stack.floats(0f,0f,0f,0f))
                    .attachmentCount(1)
                    .pAttachments(colorBlendAttachments);

            VkPipelineDepthStencilStateCreateInfo depthStencilStateInfo = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .depthTestEnable(depthState.depthTestEnable())
                    .depthWriteEnable(depthState.depthWriteEnable())
                    .depthCompareOp(depthState.depthCompareOp().qualifier())
                    .depthBoundsTestEnable(depthState.depthBoundsTestEnable())
                    .stencilTestEnable(depthState.stencilTestEnable())
                    .front(depthState.front()::consume)
                    .back(depthState.back()::consume)
                    .minDepthBounds(depthState.minDepthBounds())
                    .maxDepthBounds(depthState.maxDepthBounds());

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType$Default()
                    .stageCount(shaderStages.size())
                    .pStages(shaderStageInfos)
                    .renderPass(renderPass.handle())
                    .pMultisampleState(multisampleStateInfo)
                    .pRasterizationState(rasterizationStateInfo)
                    .pDynamicState(dynamicStateInfo)
                    .pViewportState(viewportStateInfo)
                    .pColorBlendState(colorBlendStateInfo)
                    .pDepthStencilState(depthStencilStateInfo)
                    .layout(pipelineLayout);

            VkUtil.check(vkCreateGraphicsPipelines(device.handle(), VK_NULL_HANDLE, pipelineInfo, null, pOut));
            this.handle = pOut.get(0);
        }
    }

    public void label(String name) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDebugUtilsObjectNameInfoEXT nameInfo = VkDebugUtilsObjectNameInfoEXT.calloc(stack)
                    .sType$Default()
                    .objectType(VK_OBJECT_TYPE_PIPELINE)
                    .objectHandle(this.handle)
                    .pObjectName(stack.UTF8(name + " Pipeline"));

            vkSetDebugUtilsObjectNameEXT(this.device.handle(), nameInfo);
        }
    }

    public long layout() {
        return pipelineLayout;
    }

    public long handle() {
        return handle;
    }

    @Override
    public void close() {
        vkDestroyPipeline(device.handle(), handle, null);
        vkDestroyPipelineLayout(device.handle(), pipelineLayout, null);
        super.close();
    }
}
