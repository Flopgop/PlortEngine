package net.flamgop.borked.renderer.pipeline;

import static org.lwjgl.vulkan.VK10.VK_FRONT_FACE_CLOCKWISE;
import static org.lwjgl.vulkan.VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE;

public enum FrontFace {
    COUNTER_CLOCKWISE(VK_FRONT_FACE_COUNTER_CLOCKWISE),
    CLOCKWISE(VK_FRONT_FACE_CLOCKWISE),

    ;
    private final int vkQualifier;
    FrontFace(int vkQualifier) {
        this.vkQualifier = vkQualifier;
    }
    public int qualifier() {
        return vkQualifier;
    }
}
