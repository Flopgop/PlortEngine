package net.flamgop.borked.engine.pipeline;

import org.lwjgl.vulkan.VkStencilOpState;

public record PlortDepthStencilState(boolean depthTestEnable, boolean depthWriteEnable, CompareOp depthCompareOp, boolean depthBoundsTestEnable, boolean stencilTestEnable, StencilOpState front, StencilOpState back, float minDepthBounds, float maxDepthBounds) {

    public record StencilOpState(StencilOp failOp, StencilOp passOp, StencilOp depthFailOp, CompareOp compareOp, int compareMask, int writeMask, int reference) {
        public StencilOpState() {
            this(StencilOp.KEEP, StencilOp.KEEP, StencilOp.KEEP, CompareOp.ALWAYS, 0, 0, 0);
        }

        public void consume(VkStencilOpState state) {
            state
                    .failOp(failOp.qualifier())
                    .passOp(passOp.qualifier())
                    .depthFailOp(depthFailOp.qualifier())
                    .compareOp(compareOp.qualifier())
                    .compareMask(compareMask)
                    .writeMask(writeMask)
                    .reference(reference);
        }
    }

    public PlortDepthStencilState() {
        this(true, true, CompareOp.LESS, false, false, new StencilOpState(), new StencilOpState(), 0, 0);
    }
}
