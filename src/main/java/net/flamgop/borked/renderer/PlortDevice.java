package net.flamgop.borked.renderer;

import net.flamgop.borked.renderer.descriptor.BufferDescriptorWrite;
import net.flamgop.borked.renderer.descriptor.DescriptorWrite;
import net.flamgop.borked.renderer.descriptor.TextureDescriptorWrite;
import net.flamgop.borked.renderer.memory.TrackedCloseable;
import net.flamgop.borked.renderer.util.VkUtil;
import net.flamgop.borked.renderer.exception.VulkanException;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import javax.annotation.Nullable;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

import static org.lwjgl.vulkan.EXTDebugUtils.vkSetDebugUtilsObjectNameEXT;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.VK13.*;

public class PlortDevice extends TrackedCloseable {
    private final VkDevice handle;

    private final int graphicsQueueFamily, presentQueueFamily;
    private final VkQueue graphicsQueue, presentQueue;
    private final boolean identicalQueues;

    public PlortDevice(VkPhysicalDevice device, long surface) {
        super();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pQueueFamilyCount = stack.callocInt(1);
            vkGetPhysicalDeviceQueueFamilyProperties(device, pQueueFamilyCount, null);
            VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.calloc(pQueueFamilyCount.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(device, pQueueFamilyCount, queueFamilies);

            int graphicsQueueFamily = -1;
            int presentQueueFamily = -1;
            IntBuffer pSupported = stack.callocInt(1);
            for (int i = 0; i < queueFamilies.capacity(); i++) {
                if (graphicsQueueFamily != -1 && presentQueueFamily != -1) break;
                VkQueueFamilyProperties queueFamily = queueFamilies.get(i);
                if ((queueFamily.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0 && graphicsQueueFamily == -1) {
                    graphicsQueueFamily = i;
                }
                vkGetPhysicalDeviceSurfaceSupportKHR(device, i, surface, pSupported);
                if (pSupported.get(0) == VK_TRUE && presentQueueFamily == -1) {
                    presentQueueFamily = i;
                }
            }
            if (graphicsQueueFamily == -1 || presentQueueFamily == -1) throw new VulkanException("Bad device");
            this.graphicsQueueFamily = graphicsQueueFamily;
            this.presentQueueFamily = presentQueueFamily;
            identicalQueues = graphicsQueueFamily == presentQueueFamily;

            int[] queues = new int[identicalQueues ? 1 : 2];
            if (identicalQueues) queues[0] = graphicsQueueFamily;
            else {
                queues[0] = graphicsQueueFamily;
                queues[1] = presentQueueFamily;
            }

            FloatBuffer priorities = stack.floats(1.0f);
            VkDeviceQueueCreateInfo.Buffer createInfos = VkDeviceQueueCreateInfo.calloc(queues.length, stack);
            for (int i = 0; i < queues.length; i++) {
                //noinspection resource
                createInfos.get(i)
                        .sType$Default()
                        .queueFamilyIndex(queues[i])
                        .pQueuePriorities(priorities);
            }

            PointerBuffer deviceRet = stack.callocPointer(1);

            VkPhysicalDeviceComputeShaderDerivativesFeaturesKHR computeShaderDerivativesFeatures = VkPhysicalDeviceComputeShaderDerivativesFeaturesKHR.calloc(stack)
                    .sType$Default()
                    .computeDerivativeGroupQuads(true);

            VkPhysicalDeviceMeshShaderFeaturesEXT enabledMeshFeatures = VkPhysicalDeviceMeshShaderFeaturesEXT.calloc(stack)
                    .sType$Default()
                    .meshShader(true)
                    .taskShader(true)
                    .pNext(computeShaderDerivativesFeatures.address());

            VkPhysicalDeviceVulkan13Features vk13Features = VkPhysicalDeviceVulkan13Features.calloc(stack)
                    .sType$Default()
                    .shaderDemoteToHelperInvocation(true)
                    .pNext(enabledMeshFeatures.address());

            VkPhysicalDeviceVulkan12Features vk12Features = VkPhysicalDeviceVulkan12Features.calloc(stack)
                    .sType$Default()
                    .vulkanMemoryModelDeviceScope(true)
                    .storageBuffer8BitAccess(true)
                    .runtimeDescriptorArray(true)
                    .bufferDeviceAddress(true)
                    .timelineSemaphore(true)
                    .vulkanMemoryModel(true)
                    .scalarBlockLayout(true)
                    .pNext(vk13Features.address());

            VkPhysicalDeviceVulkan11Features vk11Features = VkPhysicalDeviceVulkan11Features.calloc(stack)
                    .sType$Default()
                    .variablePointersStorageBuffer(true)
                    .pNext(vk12Features.address());

            VkPhysicalDeviceFeatures features = VkPhysicalDeviceFeatures.calloc(stack)
                    .vertexPipelineStoresAndAtomics(true)
                    .fragmentStoresAndAtomics(true)
                    .shaderInt64(true);

            PointerBuffer deviceExtensions = stack.callocPointer(3);
            deviceExtensions
                    .put(stack.UTF8(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME))
                    .put(stack.UTF8(EXTMeshShader.VK_EXT_MESH_SHADER_EXTENSION_NAME))
                    .put(stack.UTF8(KHRComputeShaderDerivatives.VK_KHR_COMPUTE_SHADER_DERIVATIVES_EXTENSION_NAME));
            deviceExtensions.flip();

            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pEnabledFeatures(features)
                    .pNext(vk11Features.address())
                    .ppEnabledExtensionNames(deviceExtensions)
                    .pQueueCreateInfos(createInfos);

            VkUtil.check(vkCreateDevice(device, createInfo, null, deviceRet));
            this.handle = new VkDevice(deviceRet.get(0), device, createInfo);

            PointerBuffer pQueue = stack.callocPointer(1);
            vkGetDeviceQueue(this.handle, graphicsQueueFamily, 0, pQueue);
            this.graphicsQueue = new VkQueue(pQueue.get(0), this.handle);
            vkGetDeviceQueue(this.handle, presentQueueFamily, 0, pQueue);
            this.presentQueue = new VkQueue(pQueue.get(0), this.handle);
        }
    }

    public void label(String name) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDebugUtilsObjectNameInfoEXT nameInfo = VkDebugUtilsObjectNameInfoEXT.calloc(stack)
                    .sType$Default()
                    .objectType(VK_OBJECT_TYPE_DEVICE)
                    .objectHandle(this.handle.address())
                    .pObjectName(stack.UTF8(name + " Device"));

            vkSetDebugUtilsObjectNameEXT(this.handle, nameInfo);
        }
    }

    public void updateDescriptorSets(@Nullable VkWriteDescriptorSet.Buffer writes, @Nullable VkCopyDescriptorSet.Buffer copies) {
        vkUpdateDescriptorSets(this.handle, writes, copies);
    }

    @SuppressWarnings("resource")
    public void writeDescriptorSets(List<DescriptorWrite> writes) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkWriteDescriptorSet.Buffer buffer = VkWriteDescriptorSet.calloc(writes.size(), stack);

            int writeIndex = 0;
            for (DescriptorWrite write : writes) {
                buffer.get(writeIndex++)
                        .sType$Default()
                        .descriptorCount(write.count())
                        .descriptorType(write.type().qualifier())
                        .dstBinding(write.dstBinding())
                        .dstSet(write.dstSet());
                switch (write) {
                    case BufferDescriptorWrite b -> {
                        VkDescriptorBufferInfo.Buffer bufferInfos = VkDescriptorBufferInfo.calloc(b.count(), stack);
                        for (int i = 0; i < b.count(); i++) {
                            b.buffers().get(i).info(bufferInfos.get(i));
                            buffer.get(writeIndex - 1).pBufferInfo(bufferInfos);
                        }
                    }
                    case TextureDescriptorWrite t -> {
                        VkDescriptorImageInfo.Buffer imageInfos = VkDescriptorImageInfo.calloc(t.count(), stack);
                        for (int i = 0; i < t.count(); i++) {
                            if (i < t.samplers().size()) t.samplers().get(i).info(imageInfos.get(i));
                            if (i < t.images().size()) t.images().get(i).info(imageInfos.get(i));

                            imageInfos.get(i).imageLayout(t.layout().qualifier());
                        }
                        buffer.get(writeIndex - 1).pImageInfo(imageInfos);
                    }
                }
            }
            vkUpdateDescriptorSets(this.handle, buffer, null);
        }
    }

    public void waitIdle() {
        vkDeviceWaitIdle(this.handle);
    }

    public VkDevice handle() {
        return handle;
    }

    public VkQueue graphicsQueue() {
        return graphicsQueue;
    }

    public int graphicsQueueFamily() {
        return graphicsQueueFamily;
    }

    public VkQueue presentQueue() {
        return presentQueue;
    }

    public int presentQueueFamily() {
        return presentQueueFamily;
    }

    public boolean identicalQueues() {
        return identicalQueues;
    }

    @Override
    public void close() {
        vkDestroyDevice(handle, null);
        super.close();
    }
}
