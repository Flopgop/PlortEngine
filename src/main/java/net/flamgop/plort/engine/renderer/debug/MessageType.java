package net.flamgop.plort.engine.renderer.debug;

import static org.lwjgl.vulkan.EXTDebugUtils.*;

public enum MessageType {
    GENERAL(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT),
    VALIDATION(VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT),
    PERFORMANCE(VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT),
    ;
    private final int vkQualifier;
    MessageType(int vkQualifier) {
        this.vkQualifier = vkQualifier;
    }
    public int qualifier() {
        return vkQualifier;
    }
}
