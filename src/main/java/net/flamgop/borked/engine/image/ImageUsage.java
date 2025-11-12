package net.flamgop.borked.engine.image;

import static org.lwjgl.vulkan.EXTAttachmentFeedbackLoopLayout.VK_IMAGE_USAGE_ATTACHMENT_FEEDBACK_LOOP_BIT_EXT;
import static org.lwjgl.vulkan.EXTFragmentDensityMap.VK_IMAGE_USAGE_FRAGMENT_DENSITY_MAP_BIT_EXT;
import static org.lwjgl.vulkan.KHRVideoDecodeQueue.*;
import static org.lwjgl.vulkan.KHRVideoEncodeQuantizationMap.VK_IMAGE_USAGE_VIDEO_ENCODE_EMPHASIS_MAP_BIT_KHR;
import static org.lwjgl.vulkan.KHRVideoEncodeQuantizationMap.VK_IMAGE_USAGE_VIDEO_ENCODE_QUANTIZATION_DELTA_MAP_BIT_KHR;
import static org.lwjgl.vulkan.KHRVideoEncodeQueue.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK14.VK_IMAGE_USAGE_HOST_TRANSFER_BIT;

public class ImageUsage {
    public static final int TRANSFER_SRC_BIT = VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
    public static final int TRANSFER_DST_BIT = VK_IMAGE_USAGE_TRANSFER_DST_BIT;
    public static final int SAMPLED_BIT = VK_IMAGE_USAGE_SAMPLED_BIT;
    public static final int STORAGE_BIT = VK_IMAGE_USAGE_STORAGE_BIT;
    public static final int COLOR_ATTACHMENT_BIT = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    public static final int DEPTH_STENCIL_ATTACHMENT_BIT = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
    public static final int TRANSIENT_ATTACHMENT_BIT = VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT;
    public static final int INPUT_ATTACHMENT_BIT = VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT;

    public static final int HOST_TRANSFER_BIT = VK_IMAGE_USAGE_HOST_TRANSFER_BIT;

    public static final int FRAGMENT_DENSITY_MAP_BIT_EXT = VK_IMAGE_USAGE_FRAGMENT_DENSITY_MAP_BIT_EXT;
    public static final int FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_KHR = VK_IMAGE_USAGE_FRAGMENT_DENSITY_MAP_BIT_EXT;
    public static final int VIDEO_DECODE_DST_BIT_KHR = VK_IMAGE_USAGE_VIDEO_DECODE_DST_BIT_KHR;
    public static final int VIDEO_DECODE_SRC_BIT_KHR = VK_IMAGE_USAGE_VIDEO_DECODE_SRC_BIT_KHR;
    public static final int VIDEO_DECODE_DPB_BIT_KHR = VK_IMAGE_USAGE_VIDEO_DECODE_DPB_BIT_KHR;
    public static final int VIDEO_ENCODE_DST_BIT_KHR = VK_IMAGE_USAGE_VIDEO_ENCODE_DST_BIT_KHR;
    public static final int VIDEO_ENCODE_SRC_BIT_KHR = VK_IMAGE_USAGE_VIDEO_ENCODE_SRC_BIT_KHR;
    public static final int VIDEO_ENCODE_DPB_BIT_KHR = VK_IMAGE_USAGE_VIDEO_ENCODE_DPB_BIT_KHR;
    public static final int ATTACHMENT_FEEDBACK_LOOP_BIT_EXT = VK_IMAGE_USAGE_ATTACHMENT_FEEDBACK_LOOP_BIT_EXT;
    public static final int VIDEO_ENCODE_QUANTIZATION_DELTA_MAP_BIT_KHR = VK_IMAGE_USAGE_VIDEO_ENCODE_QUANTIZATION_DELTA_MAP_BIT_KHR;
    public static final int VIDEO_ENCODE_EMPHASIS_MAP_BIT_KHR = VK_IMAGE_USAGE_VIDEO_ENCODE_EMPHASIS_MAP_BIT_KHR;
}
