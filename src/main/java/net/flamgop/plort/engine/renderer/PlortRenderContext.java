package net.flamgop.plort.engine.renderer;

import net.flamgop.plort.engine.renderer.debug.MessageSeverity;
import net.flamgop.plort.engine.renderer.debug.MessageType;
import net.flamgop.plort.engine.renderer.debug.PlortDebugMessenger;
import net.flamgop.plort.engine.renderer.memory.PlortAllocator;
import net.flamgop.plort.engine.renderer.swapchain.PlortSwapchain;
import net.flamgop.plort.engine.renderer.util.VkUtil;
import net.flamgop.plort.engine.renderer.exception.VulkanException;
import net.flamgop.plort.engine.renderer.window.PlortWindow;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;

import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK14.*;

public class PlortRenderContext implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlortRenderContext.class);

    private final PlortInstance instance;
    private final PlortDevice device;
    private final PlortAllocator allocator;
    private final PlortWindow window;
    private final PlortSwapchain swapchain;

    private final PlortCommandPool drawCommandPool;

    private final PlortDebugMessenger debugMessenger;

    private Runnable onSwapchainInvalidate = () -> {};

    public PlortRenderContext(String appName, int appVersion) {
        GLFW.glfwInit();
        if (!GLFWVulkan.glfwVulkanSupported()) throw new VulkanException("Vulkan is not supported on this platform!");

        instance = new PlortInstance(VK_API_VERSION_1_4, "Plort Engine", PlortInstance.makeVersion(1,0,0,0), appName, appVersion, List.of("VK_LAYER_KHRONOS_validation"), List.of(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME), true);
        window = new PlortWindow(instance, "Plort Engine", 1280, 720);
        debugMessenger = new PlortDebugMessenger(instance, EnumSet.allOf(MessageSeverity.class), EnumSet.allOf(MessageType.class));

        VkPhysicalDevice physicalDevice = PlortDevice.selectBestPhysicalDevice(instance);
        device = new PlortDevice(physicalDevice, window.surface());
        swapchain = new PlortSwapchain(device, window.surface());

        allocator = new PlortAllocator(device);

        drawCommandPool = new PlortCommandPool(device, device.graphicsQueueFamily(), swapchain.imageCount(), PlortCommandPool.CommandBufferLevel.PRIMARY, VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
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
        device.waitIdle();
        swapchain.recreate();

        drawCommandPool.recreate();
        onSwapchainInvalidate.run();
    }

    public int acquireNextImage(int syncSlot) {
        int nextImage = swapchain.acquireNextImage(syncSlot);
        if (nextImage == -1) invalidateSwapchain();
        return nextImage;
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

    @Override
    public void close() {
        drawCommandPool.close();
        swapchain.close();
        window.close();
        allocator.close();
        device.close();
        debugMessenger.close();
        instance.close();
    }
}
