package net.flamgop.borked.renderer.pipeline;

import static org.lwjgl.vulkan.EXTConditionalRendering.VK_PIPELINE_STAGE_CONDITIONAL_RENDERING_BIT_EXT;
import static org.lwjgl.vulkan.EXTDeviceGeneratedCommands.VK_PIPELINE_STAGE_COMMAND_PREPROCESS_BIT_EXT;
import static org.lwjgl.vulkan.EXTFragmentDensityMap.VK_PIPELINE_STAGE_FRAGMENT_DENSITY_PROCESS_BIT_EXT;
import static org.lwjgl.vulkan.EXTMeshShader.VK_PIPELINE_STAGE_MESH_SHADER_BIT_EXT;
import static org.lwjgl.vulkan.EXTMeshShader.VK_PIPELINE_STAGE_TASK_SHADER_BIT_EXT;
import static org.lwjgl.vulkan.EXTTransformFeedback.VK_PIPELINE_STAGE_TRANSFORM_FEEDBACK_BIT_EXT;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR;
import static org.lwjgl.vulkan.KHRFragmentShadingRate.VK_PIPELINE_STAGE_FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_PIPELINE_STAGE_NONE_KHR;
import static org.lwjgl.vulkan.NVDeviceGeneratedCommands.VK_PIPELINE_STAGE_COMMAND_PREPROCESS_BIT_NV;
import static org.lwjgl.vulkan.NVMeshShader.VK_PIPELINE_STAGE_MESH_SHADER_BIT_NV;
import static org.lwjgl.vulkan.NVMeshShader.VK_PIPELINE_STAGE_TASK_SHADER_BIT_NV;
import static org.lwjgl.vulkan.NVRayTracing.VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_NV;
import static org.lwjgl.vulkan.NVRayTracing.VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_NV;
import static org.lwjgl.vulkan.NVShadingRateImage.VK_PIPELINE_STAGE_SHADING_RATE_IMAGE_BIT_NV;
import static org.lwjgl.vulkan.VK14.*;

public class PipelineStage {
    public static final int TOP_OF_PIPE_BIT                     = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
    public static final int DRAW_INDIRECT_BIT                   = VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT;
    public static final int VERTEX_INPUT_BIT                    = VK_PIPELINE_STAGE_VERTEX_INPUT_BIT;
    public static final int VERTEX_SHADER_BIT                   = VK_PIPELINE_STAGE_VERTEX_SHADER_BIT;
    public static final int TESSELLATION_CONTROL_SHADER_BIT     = VK_PIPELINE_STAGE_TESSELLATION_CONTROL_SHADER_BIT;
    public static final int TESSELLATION_EVALUATION_SHADER_BIT  = VK_PIPELINE_STAGE_TESSELLATION_EVALUATION_SHADER_BIT;
    public static final int GEOMETRY_SHADER_BIT                 = VK_PIPELINE_STAGE_GEOMETRY_SHADER_BIT;
    public static final int FRAGMENT_SHADER_BIT                 = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
    public static final int EARLY_FRAGMENT_TESTS_BIT            = VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
    public static final int LATE_FRAGMENT_TESTS_BIT             = VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
    public static final int COLOR_ATTACHMENT_OUTPUT_BIT         = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    public static final int COMPUTE_SHADER_BIT                  = VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
    public static final int TRANSFER_BIT                        = VK_PIPELINE_STAGE_TRANSFER_BIT;
    public static final int BOTTOM_OF_PIPE_BIT                  = VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT;
    public static final int HOST_BIT                            = VK_PIPELINE_STAGE_HOST_BIT;
    public static final int ALL_GRAPHICS_BIT                    = VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT;
    public static final int ALL_COMMANDS_BIT                    = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;

    public static final int NONE                                        = VK_PIPELINE_STAGE_NONE; // VK_VERSION_1_3
    public static final int TRANSFORM_FEEDBACK_BIT_EXT                  = VK_PIPELINE_STAGE_TRANSFORM_FEEDBACK_BIT_EXT; // VK_EXT_transform_feedback
    public static final int CONDITIONAL_RENDERING_BIT_EXT               = VK_PIPELINE_STAGE_CONDITIONAL_RENDERING_BIT_EXT; // VK_EXT_conditional_rendering
    public static final int ACCELERATION_STRUCTURE_BUILD_BIT_KHR        = VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR; // VK_KHR_acceleration_structure
    public static final int RAY_TRACING_SHADER_BIT_KHR                  = VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR; // VK_KHR_ray_tracing_pipeline
    public static final int FRAGMENT_DENSITY_PROCESS_BIT_EXT            = VK_PIPELINE_STAGE_FRAGMENT_DENSITY_PROCESS_BIT_EXT; // VK_EXT_fragment_density_map
    public static final int FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_KHR    = VK_PIPELINE_STAGE_FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_KHR; // VK_KHR_fragment_shading_rate
    public static final int TASK_SHADER_BIT_EXT                         = VK_PIPELINE_STAGE_TASK_SHADER_BIT_EXT; // VK_EXT_mesh_shader
    public static final int MESH_SHADER_BIT_EXT                         = VK_PIPELINE_STAGE_MESH_SHADER_BIT_EXT; // VK_EXT_mesh_shader
    public static final int COMMAND_PREPROCESS_BIT_EXT                  = VK_PIPELINE_STAGE_COMMAND_PREPROCESS_BIT_EXT; // VK_EXT_device_generated_commands
    public static final int SHADING_RATE_IMAGE_BIT_NV                   = VK_PIPELINE_STAGE_SHADING_RATE_IMAGE_BIT_NV; // VK_NV_shading_rate_image
    public static final int RAY_TRACING_SHADER_BIT_NV                   = VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_NV; // VK_NV_ray_tracing
    public static final int ACCELERATION_STRUCTURE_BUILD_BIT_NV         = VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_NV; // VK_NV_ray_tracing
    public static final int TASK_SHADER_BIT_NV                          = VK_PIPELINE_STAGE_TASK_SHADER_BIT_NV; // VK_NV_mesh_shader
    public static final int MESH_SHADER_BIT_NV                          = VK_PIPELINE_STAGE_MESH_SHADER_BIT_NV; // VK_NV_mesh_shader
    public static final int COMMAND_PREPROCESS_BIT_NV                   = VK_PIPELINE_STAGE_COMMAND_PREPROCESS_BIT_NV; // VK_NV_device_generated_commands
    public static final int NONE_KHR                                    = VK_PIPELINE_STAGE_NONE_KHR; // VK_KHR_synchronization2
}
