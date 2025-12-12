package net.flamgop.borked.renderer.image;

import static org.lwjgl.vulkan.VK10.*;

public class AspectMask {
    public static final int COLOR_BIT = VK_IMAGE_ASPECT_COLOR_BIT;
    public static final int DEPTH_BIT = VK_IMAGE_ASPECT_DEPTH_BIT;
    public static final int STENCIL_BIT = VK_IMAGE_ASPECT_STENCIL_BIT;
    public static final int METADATA_BIT = VK_IMAGE_ASPECT_METADATA_BIT;
}
