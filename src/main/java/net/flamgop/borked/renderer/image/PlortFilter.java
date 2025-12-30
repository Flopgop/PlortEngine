package net.flamgop.borked.renderer.image;

import static org.lwjgl.vulkan.EXTFilterCubic.VK_FILTER_CUBIC_EXT;
import static org.lwjgl.vulkan.IMGFilterCubic.VK_FILTER_CUBIC_IMG;
import static org.lwjgl.vulkan.VK10.VK_FILTER_LINEAR;
import static org.lwjgl.vulkan.VK10.VK_FILTER_NEAREST;

public enum PlortFilter {
    NEAREST(VK_FILTER_NEAREST),
    LINEAR(VK_FILTER_LINEAR),
    CUBIC_EXT(VK_FILTER_CUBIC_EXT),
    CUBIC_IMG(VK_FILTER_CUBIC_IMG)

    ;
    private final int vkQualifier;
    PlortFilter(int vkQualifier) {
        this.vkQualifier = vkQualifier;
    }
    public int qualifier() {
        return vkQualifier;
    }
}
