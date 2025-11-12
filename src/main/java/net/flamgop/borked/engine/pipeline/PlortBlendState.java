package net.flamgop.borked.engine.pipeline;

import static org.lwjgl.vulkan.VK10.*;

public record PlortBlendState(boolean enable, BlendFactor srcColorBlendFactor, BlendFactor dstColorBlendFactor, BlendOp colorBlendOp, BlendFactor srcAlphaBlendFactor, BlendFactor dstAlphaBlendFactor, BlendOp alphaBlendOp, int colorWriteMask) {

    public enum BlendFactor {
        ZERO(VK_BLEND_FACTOR_ZERO),
        ONE(VK_BLEND_FACTOR_ONE),
        SRC_COLOR(VK_BLEND_FACTOR_SRC_COLOR),
        ONE_MINUS_SRC_COLOR(VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR),
        DST_COLOR(VK_BLEND_FACTOR_DST_COLOR),
        ONE_MINUS_DST_COLOR(VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR),
        SRC_ALPHA(VK_BLEND_FACTOR_SRC_ALPHA),
        ONE_MINUS_SRC_ALPHA(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA),
        DST_ALPHA(VK_BLEND_FACTOR_DST_ALPHA),
        ONE_MINUS_DST_ALPHA(VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA),
        CONSTANT_COLOR(VK_BLEND_FACTOR_CONSTANT_COLOR),
        ONE_MINUS_CONSTANT_COLOR(VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR),
        CONSTANT_ALPHA(VK_BLEND_FACTOR_CONSTANT_ALPHA),
        ONE_MINUS_CONSTANT_ALPHA(VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA),
        SRC_ALPHA_SATURATE(VK_BLEND_FACTOR_SRC_ALPHA_SATURATE),
        SRC1_COLOR(VK_BLEND_FACTOR_SRC1_COLOR),
        ONE_MINUS_SRC1_COLOR(VK_BLEND_FACTOR_ONE_MINUS_SRC1_COLOR),
        SRC1_ALPHA(VK_BLEND_FACTOR_SRC1_ALPHA),
        ONE_MINUS_SRC1_ALPHA(VK_BLEND_FACTOR_ONE_MINUS_SRC1_ALPHA),

        ;

        final int vkQualifier;
        BlendFactor(int vkQualifier) {
            this.vkQualifier = vkQualifier;
        }
        public int qualifier() {
            return vkQualifier;
        }
    }

    public enum BlendOp {
        ADD(VK_BLEND_OP_ADD),
        SUBTRACT(VK_BLEND_OP_SUBTRACT),
        REVERSE_SUBTRACT(VK_BLEND_OP_REVERSE_SUBTRACT),
        MIN(VK_BLEND_OP_MIN),
        MAX(VK_BLEND_OP_MAX),

        ;

        final int vkQualifier;
        BlendOp(int vkQualifier) { this.vkQualifier = vkQualifier; }
        public int qualifier() { return vkQualifier; }
    }

    public static PlortBlendState disabled() {
        return new PlortBlendState(
                false,
                BlendFactor.ONE,
                BlendFactor.ZERO,
                BlendOp.ADD,
                BlendFactor.ONE,
                BlendFactor.ZERO,
                BlendOp.ADD,
                VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT
        );
    }

    public static PlortBlendState alphaBlend() {
        return new PlortBlendState(
                true,
                BlendFactor.SRC_ALPHA,
                BlendFactor.ONE_MINUS_SRC_ALPHA,
                BlendOp.ADD,
                BlendFactor.ONE,
                BlendFactor.ZERO,
                BlendOp.ADD,
                VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT
        );
    }
}
