package net.flamgop.borked.engine;

import net.flamgop.borked.engine.memory.TrackedCloseable;
import net.flamgop.borked.engine.util.VkUtil;
import net.flamgop.borked.engine.util.VulkanException;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import javax.annotation.Nullable;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

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

            VkPhysicalDeviceVulkan13Features vk13Features = VkPhysicalDeviceVulkan13Features.calloc(stack)
                    .sType$Default()
                    .shaderDemoteToHelperInvocation(true);

            VkPhysicalDeviceMeshShaderFeaturesEXT enabledMeshFeatures = VkPhysicalDeviceMeshShaderFeaturesEXT.calloc(stack)
                    .sType$Default()
                    .meshShader(true)
                    .taskShader(true)
                    .pNext(vk13Features.address());

            PointerBuffer deviceExtensions = stack.callocPointer(2);
            deviceExtensions
                    .put(stack.UTF8(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME))
                    .put(stack.UTF8(EXTMeshShader.VK_EXT_MESH_SHADER_EXTENSION_NAME));
            deviceExtensions.flip();

            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pNext(enabledMeshFeatures.address())
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
