package net.flamgop.borked.renderer.pipeline;

import static org.lwjgl.vulkan.VK10.*;

public enum PipelineBindPoint {
    GRAPHICS(VK_PIPELINE_BIND_POINT_GRAPHICS),
    COMPUTE(VK_PIPELINE_BIND_POINT_COMPUTE),

    ;

    final int vkQualifier;
    PipelineBindPoint(int vkQualifier) {
        this.vkQualifier = vkQualifier;
    }
    public int qualifier() {
        return vkQualifier;
    }
}
