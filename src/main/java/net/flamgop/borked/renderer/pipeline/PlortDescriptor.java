package net.flamgop.borked.renderer.pipeline;

import static org.lwjgl.vulkan.EXTMutableDescriptorType.*;
import static org.lwjgl.vulkan.QCOMImageProcessing.*;
import static org.lwjgl.vulkan.VK14.*;

public record PlortDescriptor(Type type, int count, int stageFlags) {
    public enum Type {
        SAMPLER(VK_DESCRIPTOR_TYPE_SAMPLER),
        COMBINED_IMAGE_SAMPLER(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER),
        SAMPLED_IMAGE(VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE),
        STORAGE_IMAGE(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE),
        UNIFORM_TEXEL_BUFFER(VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER),
        STORAGE_TEXEL_BUFFER(VK_DESCRIPTOR_TYPE_STORAGE_TEXEL_BUFFER),
        UNIFORM_BUFFER(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER),
        STORAGE_BUFFER(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER),
        UNIFORM_BUFFER_DYNAMIC(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC),
        STORAGE_BUFFER_DYNAMIC(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC),
        INPUT_ATTACHMENT(VK_DESCRIPTOR_TYPE_INPUT_ATTACHMENT),
        INLINE_UNIFORM_BLOCK(VK_DESCRIPTOR_TYPE_INLINE_UNIFORM_BLOCK),
        MUTABLE_EXT(VK_DESCRIPTOR_TYPE_MUTABLE_EXT),
        SAMPLE_WEIGHT_IMAGE_QCOM(VK_DESCRIPTOR_TYPE_SAMPLE_WEIGHT_IMAGE_QCOM),
        BLOCK_MATCH_IMAGE_QCOM(VK_DESCRIPTOR_TYPE_BLOCK_MATCH_IMAGE_QCOM)

        ;
        final int vkQualifier;
        Type(int vkQualifier) {
            this.vkQualifier = vkQualifier;
        }
        public int qualifier() {
            return vkQualifier;
        }
    }
}
