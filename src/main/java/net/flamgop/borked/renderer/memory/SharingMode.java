package net.flamgop.borked.renderer.memory;

import static org.lwjgl.vulkan.VK10.*;

public enum SharingMode {
    EXCLUSIVE(VK_SHARING_MODE_EXCLUSIVE),
    CONCURRENT(VK_SHARING_MODE_CONCURRENT),

    ;
    final int vkQualifier;
    SharingMode(int vkQualifier) {
        this.vkQualifier = vkQualifier;
    }
    public int qualifier() {
        return vkQualifier;
    }
}
