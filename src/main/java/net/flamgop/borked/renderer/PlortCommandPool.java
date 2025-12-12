package net.flamgop.borked.renderer;

import net.flamgop.borked.renderer.memory.TrackedCloseable;
import net.flamgop.borked.renderer.util.VkUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.function.Consumer;

import static org.lwjgl.vulkan.EXTDebugUtils.vkSetDebugUtilsObjectNameEXT;
import static org.lwjgl.vulkan.VK10.*;

public class PlortCommandPool extends TrackedCloseable {

    public enum CommandBufferLevel {
        PRIMARY(VK_COMMAND_BUFFER_LEVEL_PRIMARY),
        SECONDARY(VK_COMMAND_BUFFER_LEVEL_SECONDARY),

        ;
        final int vkQualifier;
        CommandBufferLevel(int vkQualifier) {
            this.vkQualifier = vkQualifier;
        }
        public int qualifier() {
            return vkQualifier;
        }
    }

    private final PlortDevice device;
    private final int queueFamilyIndex;
    private final int commandBufferCount;
    private final CommandBufferLevel level;
    private final int flags;

    private long handle = -1;
    private final VkCommandBuffer[] commandBuffers;

    public PlortCommandPool(PlortDevice device, int queueFamilyIndex, int commandBufferCount, CommandBufferLevel level, int flags) {
        super();
        this.device = device;
        this.queueFamilyIndex = queueFamilyIndex;
        this.commandBufferCount = commandBufferCount;
        this.level = level;
        this.flags = flags;
        this.commandBuffers = new VkCommandBuffer[commandBufferCount];

        recreate();
    }

    public void label(String name) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDebugUtilsObjectNameInfoEXT nameInfo = VkDebugUtilsObjectNameInfoEXT.calloc(stack)
                    .sType$Default()
                    .objectType(VK_OBJECT_TYPE_COMMAND_POOL)
                    .objectHandle(this.handle)
                    .pObjectName(stack.UTF8(name + " Command Pool"));

            vkSetDebugUtilsObjectNameEXT(this.device.handle(), nameInfo);
        }

        for (int i = 0; i < commandBuffers.length; i++) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkDebugUtilsObjectNameInfoEXT nameInfo = VkDebugUtilsObjectNameInfoEXT.calloc(stack)
                        .sType$Default()
                        .objectType(VK_OBJECT_TYPE_COMMAND_BUFFER)
                        .objectHandle(this.commandBuffers[i].address())
                        .pObjectName(stack.UTF8(name + " Command Buffer " + i));

                vkSetDebugUtilsObjectNameEXT(this.device.handle(), nameInfo);
            }
        }
    }

    public long handle() {
        return handle;
    }

    public VkCommandBuffer commandBuffer(int index) {
        return commandBuffers[index];
    }

    public void transientSubmit(VkQueue queue, int index, Consumer<VkCommandBuffer> consumer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkResetCommandBuffer(commandBuffers[index], 0);
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

            VkUtil.check(vkBeginCommandBuffer(commandBuffers[index], beginInfo));
            consumer.accept(commandBuffers[index]);
            VkUtil.check(vkEndCommandBuffer(commandBuffers[index]));

            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                    .sType$Default()
                    .pCommandBuffers(stack.pointers(commandBuffers[index]));

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
                    .sType$Default();

            LongBuffer pFence = stack.callocLong(1);
            VkUtil.check(vkCreateFence(device.handle(), fenceInfo, null, pFence));
            long fence = pFence.get(0);
            VkUtil.check(vkQueueSubmit(queue, submitInfo, fence));

            VkUtil.check(vkWaitForFences(device.handle(), fence, true, Long.MAX_VALUE));
            vkDestroyFence(device.handle(), fence, null);
        }
    }

    public void submit(VkQueue queue, int index, long waitSemaphore, int waitDstStageMask, long signalSemaphore, long fence) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                    .sType$Default()
                    .waitSemaphoreCount(1)
                    .pWaitSemaphores(stack.longs(waitSemaphore))
                    .pWaitDstStageMask(stack.ints(waitDstStageMask))
                    .pSignalSemaphores(stack.longs(signalSemaphore))
                    .pCommandBuffers(stack.pointers(commandBuffers[index]));

            VkUtil.check(vkQueueSubmit(queue, submitInfo, fence));
        }
    }

    public void submitAll(VkQueue queue, long[] waitSemaphores, int[] waitDstStageMasks, long[] signalSemaphores, long fence) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                    .sType$Default()
                    .waitSemaphoreCount(1)
                    .pWaitSemaphores(stack.longs(waitSemaphores))
                    .pWaitDstStageMask(stack.ints(waitDstStageMasks))
                    .pSignalSemaphores(stack.longs(signalSemaphores))
                    .pCommandBuffers(stack.pointers(commandBuffers));

            VkUtil.check(vkQueueSubmit(queue, submitInfo, fence));
        }
    }

    public void reset(int flags) {
        VkUtil.check(vkResetCommandPool(device.handle(), handle, flags));
    }

    public void recreate() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (this.handle != -1) vkDestroyCommandPool(device.handle(), handle, null);

            LongBuffer pOut = stack.callocLong(1);
            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .queueFamilyIndex(queueFamilyIndex)
                    .flags(flags);


            VkUtil.check(vkCreateCommandPool(device.handle(), poolInfo, null, pOut));
            this.handle = pOut.get(0);

            VkCommandBufferAllocateInfo allocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType$Default()
                    .commandPool(this.handle)
                    .level(level.qualifier())
                    .commandBufferCount(commandBufferCount);

            PointerBuffer pCommandBuffers = stack.callocPointer(commandBufferCount);
            VkUtil.check(vkAllocateCommandBuffers(device.handle(), allocateInfo, pCommandBuffers));

            for (int i = 0; i < commandBufferCount; i++) {
                commandBuffers[i] = new VkCommandBuffer(pCommandBuffers.get(i), device.handle());
            }
        }
    }

    @Override
    public void close() {
        vkDestroyCommandPool(device.handle(), handle, null);
        super.close();
    }
}
