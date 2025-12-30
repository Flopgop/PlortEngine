package net.flamgop.borked.renderer.model;

import static org.lwjgl.vulkan.EXTIndexTypeUint8.VK_INDEX_TYPE_UINT8_EXT;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_INDEX_TYPE_NONE_KHR;
import static org.lwjgl.vulkan.KHRIndexTypeUint8.VK_INDEX_TYPE_UINT8_KHR;
import static org.lwjgl.vulkan.NVRayTracing.VK_INDEX_TYPE_NONE_NV;
import static org.lwjgl.vulkan.VK14.*;

public enum IndexType {
    UINT16(VK_INDEX_TYPE_UINT16),
    UINT32(VK_INDEX_TYPE_UINT32),
    UINT8(VK_INDEX_TYPE_UINT8),
    NONE_KHR(VK_INDEX_TYPE_NONE_KHR),
    NONE_NV(VK_INDEX_TYPE_NONE_NV),
    UINT8_EXT(VK_INDEX_TYPE_UINT8_EXT),
    UINT8_KHR(VK_INDEX_TYPE_UINT8_KHR)
    ;
    private final int vkQualifier;
    IndexType(int vkQualifier) {
        this.vkQualifier = vkQualifier;
    }

    public int qualifier() {
        return vkQualifier;
    }
}
