package net.flamgop.borked.renderer.pipeline;

public record PlortRasterizationState(boolean depthClampEnable, boolean rasterizerDiscardEnable, PolygonMode polygonMode, CullMode cullMode, FrontFace frontFace, boolean depthBiasEnable, float depthBiasConstantFactor, float depthBiasClamp, float depthBiasSlopeFactor, float lineWidth) {
    public PlortRasterizationState() {
        this(false, false, PolygonMode.FILL, CullMode.NONE, FrontFace.COUNTER_CLOCKWISE, false, 0f, 0f, 0f, 1f);
    }
}
