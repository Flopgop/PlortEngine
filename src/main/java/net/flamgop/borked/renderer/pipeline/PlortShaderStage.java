package net.flamgop.borked.renderer.pipeline;

import static org.lwjgl.vulkan.EXTMeshShader.*;
import static org.lwjgl.vulkan.HUAWEIClusterCullingShader.*;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.*;
import static org.lwjgl.vulkan.VK14.*;

public record PlortShaderStage(Stage stage, PlortShaderModule module, String entrypointName) {
    public enum Stage {
        VERTEX(VK_SHADER_STAGE_VERTEX_BIT),
        TESSELATION_CONTROL(VK_SHADER_STAGE_TESSELLATION_CONTROL_BIT),
        TESSELATION_EVALUATION(VK_SHADER_STAGE_TESSELLATION_EVALUATION_BIT),
        GEOMETRY(VK_SHADER_STAGE_GEOMETRY_BIT),
        FRAGMENT(VK_SHADER_STAGE_FRAGMENT_BIT),
        COMPUTE(VK_SHADER_STAGE_COMPUTE_BIT),
        TASK(VK_SHADER_STAGE_TASK_BIT_EXT),
        MESH(VK_SHADER_STAGE_MESH_BIT_EXT),
        CLUSTER_CULLING(VK_SHADER_STAGE_CLUSTER_CULLING_BIT_HUAWEI),
        RAYGEN(VK_SHADER_STAGE_RAYGEN_BIT_KHR),
        ANY_HIT(VK_SHADER_STAGE_ANY_HIT_BIT_KHR),
        CLOSEST_HIT(VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR),
        MISS(VK_SHADER_STAGE_MISS_BIT_KHR),
        INTERSECTION(VK_SHADER_STAGE_INTERSECTION_BIT_KHR),
        CALLABLE(VK_SHADER_STAGE_CALLABLE_BIT_KHR),

        ALL(VK_SHADER_STAGE_ALL),
        ALL_GRAPHICS(VK_SHADER_STAGE_ALL_GRAPHICS)
        ;
        final int vkQualifier;
        Stage(int vkQualifier) {
            this.vkQualifier = vkQualifier;
        }

        public int bit() {
            return vkQualifier;
        }
    }
}
