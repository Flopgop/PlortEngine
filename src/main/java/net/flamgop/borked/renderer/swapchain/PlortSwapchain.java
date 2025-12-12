package net.flamgop.borked.renderer.swapchain;

import net.flamgop.borked.renderer.PlortDevice;
import net.flamgop.borked.renderer.memory.TrackedCloseable;
import net.flamgop.borked.renderer.util.VkUtil;
import org.joml.Vector2i;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.EXTDebugUtils.vkSetDebugUtilsObjectNameEXT;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public class PlortSwapchain extends TrackedCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlortSwapchain.class);

    // for recreation
    private final PlortDevice device;
    private final long surface;

    private long handle = VK_NULL_HANDLE;
    private SwapchainImage[] images;
    private SwapchainSynchronizationObject[] syncObjects;

    private Vector2i extent;
    private int format, colorSpace;

    public PlortSwapchain(PlortDevice device, long surface) {
        super();
        this.device = device;
        this.surface = surface;
        this.recreate();
    }

    public long handle() {
        return handle;
    }

    public void label(String name) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDebugUtilsObjectNameInfoEXT nameInfo = VkDebugUtilsObjectNameInfoEXT.calloc(stack)
                    .sType$Default()
                    .objectType(VK_OBJECT_TYPE_SWAPCHAIN_KHR)
                    .objectHandle(this.handle)
                    .pObjectName(stack.UTF8(name + " Swapchain"));

            vkSetDebugUtilsObjectNameEXT(this.device.handle(), nameInfo);
        }

        for (int i = 0; i < images.length; i++) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkDebugUtilsObjectNameInfoEXT nameInfo = VkDebugUtilsObjectNameInfoEXT.calloc(stack)
                        .sType$Default()
                        .objectType(VK_OBJECT_TYPE_IMAGE)
                        .objectHandle(images[i].handle())
                        .pObjectName(stack.UTF8(name + " Swapchain Image " + i));

                vkSetDebugUtilsObjectNameEXT(this.device.handle(), nameInfo);

                VkDebugUtilsObjectNameInfoEXT name1Info = VkDebugUtilsObjectNameInfoEXT.calloc(stack)
                        .sType$Default()
                        .objectType(VK_OBJECT_TYPE_IMAGE)
                        .objectHandle(images[i].handle())
                        .pObjectName(stack.UTF8(name + " Swapchain Image Synchronization Object " + i));

                vkSetDebugUtilsObjectNameEXT(this.device.handle(), name1Info);
            }
        }
    }

    public SwapchainImage image(int index) {
        return images[index];
    }

    public SwapchainSynchronizationObject imageSyncObject(int index) {
        return syncObjects[index];
    }

    public int imageCount() {
        return images.length;
    }

    public Vector2i extent() {
        return extent;
    }

    public int format() {
        return format;
    }

    public int colorSpace() {
        return colorSpace;
    }

    public void recreate() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer ret = stack.callocLong(1);
            VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.calloc(stack);
            VkUtil.check(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device.handle().getPhysicalDevice(), surface, capabilities));

            VkExtent2D extent = capabilities.currentExtent();
            if (extent.width() == 0 || extent.height() == 0)
                return; // can't recreate yet, window is minimized or something

            int imageCount = Math.min(capabilities.minImageCount() + 1, capabilities.maxImageCount());
            LOGGER.debug("Setting up swapchain with {} images.", imageCount);

            IntBuffer pSurfaceFormatCount = stack.callocInt(1);
            VkUtil.check(vkGetPhysicalDeviceSurfaceFormatsKHR(device.handle().getPhysicalDevice(), surface, pSurfaceFormatCount, null));
            VkSurfaceFormatKHR.Buffer pSurfaceFormats = VkSurfaceFormatKHR.calloc(pSurfaceFormatCount.get(0), stack);
            VkUtil.check(vkGetPhysicalDeviceSurfaceFormatsKHR(device.handle().getPhysicalDevice(), surface, pSurfaceFormatCount, pSurfaceFormats));
            VkSurfaceFormatKHR format = null;

            for (VkSurfaceFormatKHR f : pSurfaceFormats) {
                if (f.format() == VK_FORMAT_B8G8R8A8_SRGB && f.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                    format = f;
                    break;
                }
            }
            if (format == null) format = pSurfaceFormats.get(0);

            long oldSwapchain = this.handle;
            VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                    .sType$Default()
                    .surface(surface)
                    .minImageCount(imageCount)
                    .imageFormat(format.format())
                    .imageColorSpace(format.colorSpace())
                    .imageExtent(extent)
                    .imageArrayLayers(1)
                    .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                    .imageSharingMode(device.identicalQueues() ? VK_SHARING_MODE_EXCLUSIVE : VK_SHARING_MODE_CONCURRENT)
                    .preTransform(capabilities.currentTransform())
                    .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                    .presentMode(VK_PRESENT_MODE_FIFO_KHR)
                    .clipped(true)
                    .oldSwapchain(oldSwapchain);

            VkUtil.check(vkCreateSwapchainKHR(device.handle(), createInfo, null, ret));
            this.handle = ret.get(0);
            this.extent = new Vector2i(extent.width(), extent.height());
            this.format = format.format();
            this.colorSpace = format.colorSpace();

            if (oldSwapchain != VK_NULL_HANDLE) {
                vkDestroySwapchainKHR(device.handle(), oldSwapchain, null);
            }

            IntBuffer pImageCount = stack.callocInt(1);
            VkUtil.check(vkGetSwapchainImagesKHR(device.handle(), handle, pImageCount, null));
            int numImages = pImageCount.get(0);
            LongBuffer pImages = stack.callocLong(numImages);
            VkUtil.check(vkGetSwapchainImagesKHR(device.handle(), handle, pImageCount, pImages));
            this.images = new SwapchainImage[numImages];
            if (this.syncObjects != null) for (SwapchainSynchronizationObject syncObject : syncObjects) if (syncObject != null) syncObject.close();
            this.syncObjects = new SwapchainSynchronizationObject[numImages];
            for (int i = 0; i < numImages; i++) {
                this.images[i] = new SwapchainImage(pImages.get(i));
                this.syncObjects[i] = SwapchainSynchronizationObject.createDefault(device);
            }
        }
    }

    public long[] allFences() {
        long[] fences = new long[syncObjects.length];
        for (int i = 0; i < fences.length; i++) {
            fences[i] = syncObjects[i].inFlightFence();
        }
        return fences;
    }

    @Override
    public void close() {
        for (SwapchainSynchronizationObject syncObject : syncObjects) if (syncObject != null) syncObject.close();
        vkDestroySwapchainKHR(device.handle(), handle, null);
        super.close();
    }
}
