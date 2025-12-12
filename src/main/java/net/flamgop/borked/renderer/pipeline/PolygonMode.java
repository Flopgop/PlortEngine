package net.flamgop.borked.renderer.pipeline;

import static org.lwjgl.vulkan.NVFillRectangle.VK_POLYGON_MODE_FILL_RECTANGLE_NV;
import static org.lwjgl.vulkan.VK13.*;

public enum PolygonMode {
    FILL(VK_POLYGON_MODE_FILL),
    LINE(VK_POLYGON_MODE_LINE),
    POINT(VK_POLYGON_MODE_POINT),
    FILL_RECTANGLE_NV(VK_POLYGON_MODE_FILL_RECTANGLE_NV)

    ;
    private final int vkQualifier;
    PolygonMode(int vkQualifier) {
        this.vkQualifier = vkQualifier;
    }
    public int qualifier() {
        return vkQualifier;
    }
}
