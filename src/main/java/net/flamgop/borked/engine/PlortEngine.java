package net.flamgop.borked.engine;

import net.flamgop.borked.engine.memory.PlortAllocator;
import net.flamgop.borked.engine.swapchain.PlortSwapchain;
import net.flamgop.borked.engine.util.VkUtil;
import net.flamgop.borked.engine.util.VulkanException;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.EXTValidationFeatures.VK_VALIDATION_FEATURE_ENABLE_GPU_ASSISTED_EXT;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK14.*;

public class PlortEngine implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlortEngine.class);

    private final VkInstance instance;
    private final PlortDevice device;
    private final PlortAllocator allocator;
    private final PlortWindow window;
    private final PlortSwapchain swapchain;

    private final PlortCommandPool drawCommandPool;

    private final long debugMessenger;

    private Runnable onSwapchainInvalidate = () -> {};

    public PlortEngine(String appName, int appVersion) {
        GLFW.glfwInit();
        if (!GLFWVulkan.glfwVulkanSupported()) throw new VulkanException("Vulkan is not supported on this platform!");

        instance = createInstance(appName, appVersion);
        window = new PlortWindow(instance, "Plort Engine", 1280, 720);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDebugUtilsMessengerCreateInfoEXT debugInfo = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack)
                    .sType$Default()
                    .messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT)
                    .messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)
                    .pfnUserCallback(PlortEngine::printDebugOutput);

            LongBuffer pMessenger = stack.callocLong(1);
            vkCreateDebugUtilsMessengerEXT(instance, debugInfo, null, pMessenger);
            this.debugMessenger = pMessenger.get(0);
        }

        VkPhysicalDevice physicalDevice = selectBestPhysicalDevice(instance);
        device = new PlortDevice(physicalDevice, window.surface());
        swapchain = new PlortSwapchain(device, window.surface());

        allocator = new PlortAllocator(device);

        drawCommandPool = new PlortCommandPool(device, device.graphicsQueueFamily(), swapchain.imageCount(), PlortCommandPool.CommandBufferLevel.PRIMARY, VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
    }

    private static int printDebugOutput(int messageSeverity, int messageTypes, long pCallbackData, long pUserData) {
        final String severity;
        if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) {
            severity = "ERROR";
        } else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0) {
            severity = "WARNING";
        } else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT) != 0) {
            severity = "INFO";
        } else {
            severity = "VERBOSE";
        }

        String type = "";
        if ((messageTypes & VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT) != 0) type += "GENERAL ";
        if ((messageTypes & VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT) != 0) type += "VALIDATION ";
        if ((messageTypes & VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT) != 0) type += "PERFORMANCE ";

        @SuppressWarnings("resource")
        VkDebugUtilsMessengerCallbackDataEXT callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
        String message = callbackData.pMessageString();

        LOGGER.debug("[{}][{}] {}", severity, type.trim(), message);

        return VK_FALSE;
    }

    @SuppressWarnings("UnusedReturnValue")
    public Runnable onSwapchainInvalidate(@NotNull Runnable runnable) {
        Runnable theOldOne = this.onSwapchainInvalidate;
        this.onSwapchainInvalidate = runnable;
        return theOldOne;
    }

    public PlortAllocator allocator() {
        return allocator;
    }

    public PlortDevice device() {
        return device;
    }

    public PlortWindow window() {
        return window;
    }

    public PlortSwapchain swapchain() {
        return swapchain;
    }

    public boolean running() {
        return !window.shouldClose();
    }

    public VkCommandBuffer drawBuffer(int index) {
        return drawCommandPool.commandBuffer(index);
    }

    public void invalidateSwapchain() {
        LOGGER.debug("Swapchain invalidated, recreating...");
        vkDeviceWaitIdle(device.handle());
        swapchain.recreate();

        drawCommandPool.recreate();
        onSwapchainInvalidate.run();
    }

    public int acquireNextImage(int syncSlot) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pImageIndex = stack.callocInt(1);
            int result = vkAcquireNextImageKHR(device.handle(), swapchain.handle(), Long.MAX_VALUE, swapchain.imageSyncObject(syncSlot).imageAvailableSemaphore(), VK_NULL_HANDLE, pImageIndex);
            int imageIndex = pImageIndex.get(0);
            if (result == VK_ERROR_OUT_OF_DATE_KHR) {
                invalidateSwapchain();
                return -1;
            } else if (result != VK_SUBOPTIMAL_KHR) {
                VkUtil.check(result);
            }
            return imageIndex;
        }
    }

    public PlortCommandPool commandPool() {
        return this.drawCommandPool;
    }

    private static final long FENCE_TIMEOUT = 10_000_000;
    public boolean waitForFence(int syncSlot) {
        int result = vkWaitForFences(device.handle(), swapchain.imageSyncObject(syncSlot).inFlightFence(), true, FENCE_TIMEOUT);
        if (result == VK_SUCCESS) VkUtil.check(vkResetFences(device.handle(), swapchain.imageSyncObject(syncSlot).inFlightFence()));
        return result == VK_TIMEOUT;
    }

    public void submitFrame(int syncSlot, int imageIndex) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                    .sType$Default()
                    .waitSemaphoreCount(1)
                    .pWaitSemaphores(stack.longs(swapchain.imageSyncObject(syncSlot).imageAvailableSemaphore()))
                    .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
                    .pCommandBuffers(stack.pointers(drawCommandPool.commandBuffer(imageIndex).address()))
                    .pSignalSemaphores(stack.longs(swapchain.imageSyncObject(syncSlot).renderFinishedSemaphore()));

            VkUtil.check(vkQueueSubmit(device.graphicsQueue(), submitInfo, swapchain.imageSyncObject(syncSlot).inFlightFence()));
        }
    }

    /// @return true if the swapchain was invalidated.
    public boolean presentFrame(int syncSlot, int imageIndex) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack)
                    .sType$Default()
                    .pWaitSemaphores(stack.longs(swapchain.imageSyncObject(syncSlot).renderFinishedSemaphore()))
                    .swapchainCount(1)
                    .pSwapchains(stack.longs(swapchain.handle()))
                    .pImageIndices(stack.ints(imageIndex));

            int result = vkQueuePresentKHR(device.presentQueue(), presentInfo);
            if (result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_SUBOPTIMAL_KHR) {
                if (!window.minimized()) invalidateSwapchain();
                else LOGGER.debug("Window is minimized, it doesn't make sense to recreate framebuffer yet.");
                return true;
            }
        }

        return false;
    }

    private static VkInstance createInstance(String appName, int appVersion) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
            if (glfwExtensions == null) throw new VulkanException("GLFW extensions not available.");

            PointerBuffer instanceExtensions = stack.callocPointer(glfwExtensions.capacity() + 1);
            instanceExtensions.put(glfwExtensions);
            instanceExtensions.put(stack.UTF8(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME));
            instanceExtensions.flip();

            PointerBuffer validationLayers = stack.callocPointer(1);
            validationLayers.put(stack.UTF8("VK_LAYER_KHRONOS_validation")).flip();

            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                    .apiVersion(VK_API_VERSION_1_4)
                    .engineVersion(VK_MAKE_API_VERSION(1, 0, 0, 0))
                    .applicationVersion(appVersion)
                    .pEngineName(stack.UTF8("PlortEngine"))
                    .pApplicationName(stack.UTF8(appName));

            VkValidationFeaturesEXT validationFeatures = VkValidationFeaturesEXT.calloc(stack)
                    .sType$Default();
//                    .pEnabledValidationFeatures(stack.ints(VK_VALIDATION_FEATURE_ENABLE_GPU_ASSISTED_EXT));

            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pApplicationInfo(appInfo)
                    .ppEnabledExtensionNames(instanceExtensions)
                    .ppEnabledLayerNames(validationLayers)
                    .pNext(validationFeatures);

            PointerBuffer ret = stack.callocPointer(1);
            VkUtil.check(vkCreateInstance(createInfo, null, ret));
            return new VkInstance(ret.get(), createInfo);
        }
    }

    private static VkPhysicalDevice selectBestPhysicalDevice(VkInstance instance) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pDeviceCount = stack.callocInt(1);
            VkUtil.check(vkEnumeratePhysicalDevices(instance, pDeviceCount, null));
            int numDevices = pDeviceCount.get(0);
            PointerBuffer pDevices = stack.callocPointer(numDevices);
            VkUtil.check(vkEnumeratePhysicalDevices(instance, pDeviceCount, pDevices));
            VkPhysicalDevice[] physicalDevices = new VkPhysicalDevice[numDevices];
            for (int i = 0; i < numDevices; i++) {
                physicalDevices[i] = new VkPhysicalDevice(pDevices.get(i), instance);
            }

            int[] scores = new int[numDevices];
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
            VkPhysicalDeviceMemoryProperties memoryProperties = VkPhysicalDeviceMemoryProperties.calloc(stack);
            VkPhysicalDeviceFeatures2 features = VkPhysicalDeviceFeatures2.calloc(stack).sType$Default();
            VkPhysicalDeviceMeshShaderFeaturesEXT meshFeatures = VkPhysicalDeviceMeshShaderFeaturesEXT.calloc(stack).sType$Default();
            features.pNext(meshFeatures);
            for (int i = 0; i < numDevices; i++) {
                VkPhysicalDevice device = physicalDevices[i];
                vkGetPhysicalDeviceProperties(device, properties);
                if (properties.deviceType() == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) scores[i] += 1000;

                vkGetPhysicalDeviceMemoryProperties(device, memoryProperties);
                VkMemoryHeap.Buffer heaps = memoryProperties.memoryHeaps();
                for (VkMemoryHeap heap : heaps) {
                    if ((heap.flags() & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) != 0) {
                        scores[i] += (int) ((heap.size() / 1024.0) / 1024.0); // MB
                    }
                }

                try (MemoryStack stack2 = MemoryStack.stackPush()) {
                    IntBuffer pCount = stack2.callocInt(1);
                    vkEnumerateDeviceExtensionProperties(device, (ByteBuffer) null, pCount, null);
                    int count = pCount.get(0);
                    VkExtensionProperties.Buffer extensionProperties = VkExtensionProperties.calloc(count, stack2);
                    vkEnumerateDeviceExtensionProperties(device, (ByteBuffer) null, pCount, extensionProperties);
                    for (VkExtensionProperties extensionProps : extensionProperties) {
                        if (extensionProps.extensionNameString().equals(EXTMeshShader.VK_EXT_MESH_SHADER_EXTENSION_NAME)) {
                            LOGGER.debug("Device {} supports mesh shaders!", i);
                            scores[i] += 10000;
                            break;
                        }
                    }
                }

                vkGetPhysicalDeviceFeatures2(device, features);
                if (!meshFeatures.meshShader() || !meshFeatures.taskShader()) {
                    LOGGER.debug("Device {} doesn't support mesh and/or task shaders!", i);
                    scores[i] = Integer.MIN_VALUE; // unsupported.
                } else LOGGER.debug("Device {} supports mesh and task shaders!", i);
            }
            int highestScore = Integer.MIN_VALUE;
            int highestScoreIndex = 0;
            for (int i = 0; i < numDevices; i++) {
                if (scores[i] > highestScore) {
                    highestScore = scores[i];
                    highestScoreIndex = i;
                }
            }
            if (highestScore < 0) throw new VulkanException("No supported devices found!");
            LOGGER.debug("Using device {}", highestScoreIndex);
            return physicalDevices[highestScoreIndex];
        }
    }

    @Override
    public void close() {
        drawCommandPool.close();
        swapchain.close();
        window.close();
        allocator.close();
        device.close();
        vkDestroyDebugUtilsMessengerEXT(instance, debugMessenger, null);
        vkDestroyInstance(instance, null);
    }
}
