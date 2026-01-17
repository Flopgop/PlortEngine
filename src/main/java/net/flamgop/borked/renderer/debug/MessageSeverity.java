package net.flamgop.borked.renderer.debug;

import static org.lwjgl.vulkan.EXTDebugUtils.*;

public enum MessageSeverity {
    VERBOSE(VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT),
    WARNING(VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT),
    ERROR(VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT),
    INFO(VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT)
    ;

    private final int vkQualifier;
    MessageSeverity(int vkQualifier) {
        this.vkQualifier = vkQualifier;
    }
    public int qualifier() {
        return vkQualifier;
    }
}
