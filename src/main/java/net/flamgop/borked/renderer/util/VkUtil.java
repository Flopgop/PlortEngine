package net.flamgop.borked.renderer.util;

import net.flamgop.borked.renderer.exception.VulkanException;
import net.flamgop.borked.renderer.image.AspectMask;
import net.flamgop.borked.renderer.image.PlortImage;
import net.flamgop.borked.renderer.memory.PlortBuffer;
import net.flamgop.borked.renderer.pipeline.PipelineStage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkCommandBuffer;

import static org.lwjgl.vulkan.EXTBufferDeviceAddress.VK_ERROR_INVALID_DEVICE_ADDRESS_EXT;
import static org.lwjgl.vulkan.EXTDebugReport.VK_ERROR_VALIDATION_FAILED_EXT;
import static org.lwjgl.vulkan.EXTFullScreenExclusive.VK_ERROR_FULL_SCREEN_EXCLUSIVE_MODE_LOST_EXT;
import static org.lwjgl.vulkan.EXTImageCompressionControl.VK_ERROR_COMPRESSION_EXHAUSTED_EXT;
import static org.lwjgl.vulkan.EXTShaderObject.VK_INCOMPATIBLE_SHADER_BINARY_EXT;
import static org.lwjgl.vulkan.KHRDeferredHostOperations.*;
import static org.lwjgl.vulkan.KHRDisplaySwapchain.VK_ERROR_INCOMPATIBLE_DISPLAY_KHR;
import static org.lwjgl.vulkan.KHRPipelineBinary.VK_ERROR_NOT_ENOUGH_SPACE_KHR;
import static org.lwjgl.vulkan.KHRPipelineBinary.VK_PIPELINE_BINARY_MISSING_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_ERROR_NATIVE_WINDOW_IN_USE_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_ERROR_SURFACE_LOST_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
import static org.lwjgl.vulkan.KHRVideoEncodeQueue.VK_ERROR_INVALID_VIDEO_STD_PARAMETERS_KHR;
import static org.lwjgl.vulkan.KHRVideoQueue.*;
import static org.lwjgl.vulkan.NVGLSLShader.VK_ERROR_INVALID_SHADER_NV;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.vkCmdCopyBufferToImage;
import static org.lwjgl.vulkan.VK11.VK_ERROR_INVALID_EXTERNAL_HANDLE;
import static org.lwjgl.vulkan.VK11.VK_ERROR_OUT_OF_POOL_MEMORY;
import static org.lwjgl.vulkan.VK12.VK_ERROR_FRAGMENTATION;
import static org.lwjgl.vulkan.VK12.VK_ERROR_INVALID_OPAQUE_CAPTURE_ADDRESS;
import static org.lwjgl.vulkan.VK13.VK_ACCESS_NONE;
import static org.lwjgl.vulkan.VK13.VK_PIPELINE_COMPILE_REQUIRED;
import static org.lwjgl.vulkan.VK14.VK_ERROR_NOT_PERMITTED;

public class VkUtil {

    public static int makeApiVersion(int variant, int major, int minor, int patch) {
        return VK_MAKE_API_VERSION(variant, major, minor, patch);
    }

    public static String errorToString(int status) {
        return switch (status) {
            case VK_SUCCESS -> "VK_SUCCESS";
            case VK_NOT_READY -> "VK_NOT_READY";
            case VK_TIMEOUT -> "VK_TIMEOUT";
            case VK_EVENT_SET -> "VK_EVENT_SET";
            case VK_EVENT_RESET -> "VK_EVENT_RESET";
            case VK_INCOMPLETE -> "VK_INCOMPLETE";
            case VK_SUBOPTIMAL_KHR -> "VK_SUBOPTIMAL_KHR";
            case VK_THREAD_IDLE_KHR -> "VK_THREAD_IDLE_KHR";
            case VK_THREAD_DONE_KHR -> "VK_THREAD_DONE_KHR";
            case VK_OPERATION_DEFERRED_KHR -> "VK_OPERATION_DEFERRED_KHR";
            case VK_OPERATION_NOT_DEFERRED_KHR -> "VK_OPERATION_NOT_DEFERRED_KHR";
            case VK_PIPELINE_COMPILE_REQUIRED -> "VK_PIPELINE_COMPILE_REQUIRED";
            case VK_PIPELINE_BINARY_MISSING_KHR -> "VK_PIPELINE_BINARY_MISSING_KHR";
            case VK_INCOMPATIBLE_SHADER_BINARY_EXT -> "VK_INCOMPATIBLE_SHADER_BINARY_EXT";
            case VK_ERROR_OUT_OF_HOST_MEMORY -> "VK_ERROR_OUT_OF_HOST_MEMORY";
            case VK_ERROR_OUT_OF_DEVICE_MEMORY -> "VK_ERROR_OUT_OF_DEVICE_MEMORY";
            case VK_ERROR_INITIALIZATION_FAILED -> "VK_ERROR_INITIALIZATION_FAILED";
            case VK_ERROR_DEVICE_LOST -> "VK_ERROR_DEVICE_LOST";
            case VK_ERROR_MEMORY_MAP_FAILED -> "VK_ERROR_MEMORY_MAP_FAILED";
            case VK_ERROR_LAYER_NOT_PRESENT -> "VK_ERROR_LAYER_NOT_PRESENT";
            case VK_ERROR_EXTENSION_NOT_PRESENT -> "VK_ERROR_EXTENSION_NOT_PRESENT";
            case VK_ERROR_FEATURE_NOT_PRESENT -> "VK_ERROR_FEATURE_NOT_PRESENT";
            case VK_ERROR_INCOMPATIBLE_DRIVER -> "VK_ERROR_INCOMPATIBLE_DRIVER";
            case VK_ERROR_TOO_MANY_OBJECTS -> "VK_ERROR_TOO_MANY_OBJECTS";
            case VK_ERROR_FORMAT_NOT_SUPPORTED -> "VK_ERROR_FORMAT_NOT_SUPPORTED";
            case VK_ERROR_FRAGMENTED_POOL -> "VK_ERROR_FRAGMENTED_POOL";
            case VK_ERROR_OUT_OF_POOL_MEMORY -> "VK_ERROR_OUT_OF_POOL_MEMORY";
            case VK_ERROR_SURFACE_LOST_KHR -> "VK_ERROR_SURFACE_LOST_KHR";
            case VK_ERROR_NATIVE_WINDOW_IN_USE_KHR -> "VK_ERROR_NATIVE_WINDOW_IN_USE_KHR";
            case VK_ERROR_OUT_OF_DATE_KHR -> "VK_ERROR_OUT_OF_DATE_KHR";
            case VK_ERROR_INCOMPATIBLE_DISPLAY_KHR -> "VK_ERROR_INCOMPATIBLE_DISPLAY_KHR";
            case VK_ERROR_INVALID_SHADER_NV -> "VK_ERROR_INVALID_SHADER_NV";
            case VK_ERROR_INVALID_EXTERNAL_HANDLE -> "VK_ERROR_INVALID_EXTERNAL_HANDLE";
            case VK_ERROR_FRAGMENTATION -> "VK_ERROR_FRAGMENTATION";
            case VK_ERROR_INVALID_OPAQUE_CAPTURE_ADDRESS | VK_ERROR_INVALID_DEVICE_ADDRESS_EXT -> "VK_INVALID_OPAQUE_CAPTURE_ADDRESS | VK_ERROR_INVALID_DEVICE_ADDRESS_EXT";
            case VK_ERROR_FULL_SCREEN_EXCLUSIVE_MODE_LOST_EXT -> "VK_ERROR_FULL_SCREEN_EXCLUSIVE_MODE_LOST_EXT";
            case VK_ERROR_VALIDATION_FAILED_EXT -> "VK_ERROR_VALIDATION_FAILED_EXT";
            case VK_ERROR_COMPRESSION_EXHAUSTED_EXT -> "VK_ERROR_COMPRESSION_EXHAUSTED_EXT";
            case VK_ERROR_IMAGE_USAGE_NOT_SUPPORTED_KHR -> "VK_ERROR_IMAGE_USAGE_NOT_SUPPORTED_KHR";
            case VK_ERROR_VIDEO_PICTURE_LAYOUT_NOT_SUPPORTED_KHR -> "VK_ERROR_VIDEO_PICTURE_LAYOUT_NOT_SUPPORTED_KHR";
            case VK_ERROR_VIDEO_PROFILE_OPERATION_NOT_SUPPORTED_KHR -> "VK_ERROR_VIDEO_PROFILE_OPERATION_NOT_SUPPORTED_KHR";
            case VK_ERROR_VIDEO_PROFILE_FORMAT_NOT_SUPPORTED_KHR -> "VK_ERROR_VIDEO_PROFILE_FORMAT_NOT_SUPPORTED_KHR";
            case VK_ERROR_VIDEO_PROFILE_CODEC_NOT_SUPPORTED_KHR -> "VK_ERROR_VIDEO_PROFILE_CODEC_NOT_SUPPORTED_KHR";
            case VK_ERROR_VIDEO_STD_VERSION_NOT_SUPPORTED_KHR -> "VK_ERROR_VIDEO_STD_VERSION_NOT_SUPPORTED_KHR";
            case VK_ERROR_INVALID_VIDEO_STD_PARAMETERS_KHR -> "VK_ERROR_INVALID_VIDEO_STD_PARAMETERS_KHR";
            case VK_ERROR_NOT_PERMITTED -> "VK_ERROR_NOT_PERMITTED";
            case VK_ERROR_NOT_ENOUGH_SPACE_KHR -> "VK_ERROR_NOT_ENOUGH_SPACE_KHR";
            case VK_ERROR_UNKNOWN -> "VK_ERROR_UNKNOWN";
            default -> String.format("Unknown Error! %d", status);
        };
    }

    public static void check(int status) {
        if (status != VK10.VK_SUCCESS) {
            throw new VulkanException("Check returned status: " + errorToString(status));
        }
    }

    public static void copyBufferToImage(VkCommandBuffer cmdBuffer, PlortBuffer data, PlortImage image, int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            image.transitionLayout(
                    cmdBuffer,
                    PlortImage.Layout.UNDEFINED,
                    PlortImage.Layout.TRANSFER_DST_OPTIMAL,
                    PipelineStage.TOP_OF_PIPE_BIT,
                    PipelineStage.TRANSFER_BIT,
                    VK_ACCESS_NONE,
                    VK_ACCESS_TRANSFER_WRITE_BIT
            );

            VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack)
                    .bufferOffset(0)
                    .bufferRowLength(0)
                    .bufferImageHeight(0)
                    .imageSubresource(r -> r
                            .aspectMask(AspectMask.COLOR_BIT)
                            .mipLevel(0)
                            .baseArrayLayer(0)
                            .layerCount(1))
                    .imageOffset(o -> o.set(0, 0, 0))
                    .imageExtent(e -> e.set(width, height, 1));

            vkCmdCopyBufferToImage(cmdBuffer, data.handle(), image.handle(), PlortImage.Layout.TRANSFER_DST_OPTIMAL.qualifier(), region);

            image.transitionLayout(
                    cmdBuffer,
                    PlortImage.Layout.TRANSFER_DST_OPTIMAL,
                    PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL,
                    PipelineStage.TRANSFER_BIT,
                    PipelineStage.FRAGMENT_SHADER_BIT,
                    VK_ACCESS_TRANSFER_WRITE_BIT,
                    VK_ACCESS_SHADER_READ_BIT
            );
        }
    }
}
