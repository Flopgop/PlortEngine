package net.flamgop.borked.renderer.pipeline;

import static org.lwjgl.vulkan.VK10.*;

public enum CullMode {
    NONE(VK_CULL_MODE_NONE),
    FRONT(VK_CULL_MODE_FRONT_BIT),
    BACK(VK_CULL_MODE_BACK_BIT),
    FRONT_AND_BACK(VK_CULL_MODE_FRONT_AND_BACK),

    ;
    private final int vkQualifier;
    CullMode(int vkQualifier) {
        this.vkQualifier = vkQualifier;
    }
    public int qualifier() {
        return vkQualifier;
    }
}
