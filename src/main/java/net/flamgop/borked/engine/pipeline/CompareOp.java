package net.flamgop.borked.engine.pipeline;

import static org.lwjgl.vulkan.VK10.*;

public enum CompareOp {
    NEVER(VK_COMPARE_OP_NEVER),
    LESS(VK_COMPARE_OP_LESS),
    EQUAL(VK_COMPARE_OP_EQUAL),
    LESS_OR_EQUAL(VK_COMPARE_OP_LESS_OR_EQUAL),
    GREATER(VK_COMPARE_OP_GREATER),
    NOT_EQUAL(VK_COMPARE_OP_NOT_EQUAL),
    GREATER_OR_EQUAL(VK_COMPARE_OP_GREATER_OR_EQUAL),
    ALWAYS(VK_COMPARE_OP_ALWAYS),

    ;
    final int vkQualifier;
    CompareOp(int vkQualifier) {
        this.vkQualifier = vkQualifier;
    }
    public int qualifier() {
        return vkQualifier;
    }
}
