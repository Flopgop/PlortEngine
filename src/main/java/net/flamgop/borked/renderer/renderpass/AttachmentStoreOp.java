package net.flamgop.borked.renderer.renderpass;

import static org.lwjgl.vulkan.VK10.*;

public enum AttachmentStoreOp {
    STORE(VK_ATTACHMENT_STORE_OP_STORE),
    DONT_CARE(VK_ATTACHMENT_STORE_OP_DONT_CARE),
    ;
    private final int vkQualifier;
    AttachmentStoreOp(int vkQualifier) {
        this.vkQualifier = vkQualifier;
    }
    public int qualifier() {
        return vkQualifier;
    }
}
