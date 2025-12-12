package net.flamgop.borked.renderer.swapchain;

import net.flamgop.borked.renderer.PlortDevice;
import net.flamgop.borked.renderer.util.VkUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

public record SwapchainSynchronizationObject(PlortDevice device, long imageAvailableSemaphore, long renderFinishedSemaphore, long inFlightFence) implements AutoCloseable {
    public static SwapchainSynchronizationObject createDefault(PlortDevice device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack)
                    .sType$Default();

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK_FENCE_CREATE_SIGNALED_BIT);

            LongBuffer pImageSemaphore = stack.callocLong(1);
            LongBuffer pRenderSemaphore = stack.callocLong(1);
            LongBuffer pFence = stack.callocLong(1);

            VkUtil.check(vkCreateSemaphore(device.handle(), semaphoreInfo, null, pImageSemaphore));
            VkUtil.check(vkCreateSemaphore(device.handle(), semaphoreInfo, null, pRenderSemaphore));
            VkUtil.check(vkCreateFence(device.handle(), fenceInfo, null, pFence));

            return new SwapchainSynchronizationObject(device, pImageSemaphore.get(0), pRenderSemaphore.get(0), pFence.get(0));
        }
    }

    @Override
    public void close() {
        vkDestroySemaphore(device.handle(), imageAvailableSemaphore, null);
        vkDestroySemaphore(device.handle(), renderFinishedSemaphore, null);
        vkDestroyFence(device.handle(), inFlightFence, null);
    }
}
