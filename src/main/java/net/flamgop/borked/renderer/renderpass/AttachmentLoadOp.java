package net.flamgop.borked.renderer.renderpass;

import static org.lwjgl.vulkan.VK10.*;

public enum AttachmentLoadOp {
    LOAD(VK_ATTACHMENT_LOAD_OP_LOAD),
    CLEAR(VK_ATTACHMENT_LOAD_OP_CLEAR),
    DONT_CARE(VK_ATTACHMENT_LOAD_OP_DONT_CARE),

    ;
    private final int vkQualifier;
    AttachmentLoadOp(int vkQualifier) {
        this.vkQualifier = vkQualifier;
    }
    public int qualifier() {
        return vkQualifier;
    }
}
