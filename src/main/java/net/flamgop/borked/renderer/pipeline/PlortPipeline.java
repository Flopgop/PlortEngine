package net.flamgop.borked.renderer.pipeline;

import net.flamgop.borked.renderer.PlortCommandBuffer;
import net.flamgop.borked.renderer.PlortDevice;
import net.flamgop.borked.renderer.renderpass.PlortRenderPass;
import net.flamgop.borked.renderer.memory.TrackedCloseable;
import net.flamgop.borked.renderer.util.VkUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.EXTDebugUtils.vkSetDebugUtilsObjectNameEXT;
import static org.lwjgl.vulkan.VK14.*;

public class PlortPipeline extends TrackedCloseable {
    private final PlortDevice device;

    private final long handle;

    public static Builder builder(PlortDevice device, PlortRenderPass renderPass) {
        return new Builder(device, renderPass);
    }

    public static Builder builder(PlortDevice device) {
        return new Builder(device);
    }

    public static class Builder {
        private final PlortDevice device;
        private final PlortRenderPass renderPass;

        private final List<PlortShaderStage> shaderStages = new ArrayList<>();
        private final List<PlortBlendState> blendState = new ArrayList<>();

        private PlortDepthStencilState depthState = new PlortDepthStencilState();
        private PlortRasterizationState rasterizationState = new PlortRasterizationState();

        private PlortPipelineLayout layout;

        Builder(PlortDevice device, PlortRenderPass renderPass) {
            this.device = device;
            this.renderPass = renderPass;
        }

        Builder(PlortDevice device) {
            this.device = device;
            this.renderPass = null;
        }

        public Builder layout(PlortPipelineLayout layout) {
            this.layout = layout;
            return this;
        }

        public Builder shaderStage(PlortShaderStage stage) {
            if (this.renderPass == null && !shaderStages.isEmpty()) throw new UnsupportedOperationException("Cannot add multiple shader stages to compute shader!");
            shaderStages.add(stage);
            return this;
        }

        public Builder blendState(PlortBlendState blendState) {
            if (this.renderPass == null) throw new UnsupportedOperationException("Cannot add blend state to compute shader!");
            this.blendState.add(blendState);
            return this;
        }

        public Builder depthStencilStateInfo(PlortDepthStencilState depthState) {
            if (this.renderPass == null) throw new UnsupportedOperationException("Cannot add depth stencil state to compute shader!");
            this.depthState = depthState;
            return this;
        }

        public Builder rasterizationState(PlortRasterizationState rasterizationState) {
            if (this.renderPass == null) throw new UnsupportedOperationException("Cannot add rasterization state to compute shader!");
            this.rasterizationState = rasterizationState;
            return this;
        }

        public PlortPipeline buildGraphics() {
            if (layout == null) throw new NullPointerException("Pipelines require a layout!");
            if (renderPass == null) throw new UnsupportedOperationException("Cannot build a compute pipeline as a graphics pipeline!");
            return new PlortPipeline(device, renderPass, shaderStages, layout, rasterizationState, blendState, depthState);
        }

        public PlortPipeline buildCompute() {
            if (layout == null) throw new NullPointerException("Pipelines require a layout!");
            if (renderPass != null) throw new UnsupportedOperationException("Cannot build a graphics pipeline as a compute pipeline!");
            return new PlortPipeline(device, shaderStages.getFirst(), layout);
        }
    }

    @SuppressWarnings("resource")
    public PlortPipeline(PlortDevice device, PlortShaderStage shaderStage, PlortPipelineLayout pipelineLayout) {
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pOut = stack.callocLong(1);
            VkPipelineShaderStageCreateInfo shaderStageInfo = VkPipelineShaderStageCreateInfo.calloc(stack);
            shaderStageInfo
                    .sType$Default()
                    .stage(shaderStage.stage().bit())
                    .module(shaderStage.module().handle())
                    .pName(stack.UTF8(shaderStage.entrypointName()));

            VkComputePipelineCreateInfo.Buffer pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack)
                    .sType$Default()
                    .stage(shaderStageInfo)
                    .layout(pipelineLayout.handle());

            VkUtil.check(vkCreateComputePipelines(device.handle(), VK_NULL_HANDLE, pipelineInfo, null, pOut));
            this.handle = pOut.get(0);
        }
    }

    @SuppressWarnings("resource")
    public PlortPipeline(PlortDevice device, PlortRenderPass renderPass, List<PlortShaderStage> shaderStages, PlortPipelineLayout pipelineLayout, PlortRasterizationState rasterizationState, List<PlortBlendState> blendStates, PlortDepthStencilState depthState) {
        super();

        if (shaderStages.stream().anyMatch(s -> s.stage() == PlortShaderStage.Stage.COMPUTE)) {
            throw new UnsupportedOperationException("Cannot have compute shaders in a graphics pipeline!");
        }

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
                    .depthClampEnable(rasterizationState.depthClampEnable())
                    .rasterizerDiscardEnable(rasterizationState.rasterizerDiscardEnable())
                    .polygonMode(rasterizationState.polygonMode().qualifier())
                    .cullMode(rasterizationState.cullMode().qualifier())
                    .frontFace(rasterizationState.frontFace().qualifier())
                    .depthBiasEnable(rasterizationState.depthBiasEnable())
                    .depthBiasConstantFactor(rasterizationState.depthBiasConstantFactor())
                    .depthBiasClamp(rasterizationState.depthBiasClamp())
                    .depthBiasSlopeFactor(rasterizationState.depthBiasSlopeFactor())
                    .lineWidth(rasterizationState.lineWidth());

            VkPipelineDynamicStateCreateInfo dynamicStateInfo = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

            VkPipelineViewportStateCreateInfo viewportStateInfo = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .viewportCount(1)
                    .scissorCount(1);

            VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachments = VkPipelineColorBlendAttachmentState.calloc(blendStates.size(), stack);
            for (int i = 0; i < blendStates.size(); i++) {
                PlortBlendState blendState = blendStates.get(i);
                    colorBlendAttachments.get(i)
                        .blendEnable(blendState.enable())
                        .srcColorBlendFactor(blendState.srcColorBlendFactor().qualifier())
                        .dstColorBlendFactor(blendState.dstColorBlendFactor().qualifier())
                        .colorBlendOp(blendState.colorBlendOp().qualifier())
                        .srcAlphaBlendFactor(blendState.srcAlphaBlendFactor().qualifier())
                        .dstAlphaBlendFactor(blendState.dstAlphaBlendFactor().qualifier())
                        .alphaBlendOp(blendState.alphaBlendOp().qualifier())
                        .colorWriteMask(blendState.colorWriteMask());
            }

            VkPipelineColorBlendStateCreateInfo colorBlendStateInfo = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .logicOpEnable(false)
                    .blendConstants(stack.floats(0f,0f,0f,0f))
                    .attachmentCount(blendStates.size())
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
                    .layout(pipelineLayout.handle());

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

    public long handle() {
        return handle;
    }

    public void bind(PlortCommandBuffer commandBuffer, PipelineBindPoint bindPoint) {
        commandBuffer.bindPipeline(bindPoint, this);
    }

    @Override
    public void close() {
        vkDestroyPipeline(device.handle(), handle, null);
        super.close();
    }
}
