package net.flamgop.borked.renderer.window;

import net.flamgop.borked.renderer.PlortInstance;
import net.flamgop.borked.renderer.util.VkUtil;
import net.flamgop.borked.renderer.exception.VulkanException;
import org.lwjgl.glfw.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.windows.WindowsLibrary;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static org.lwjgl.glfw.GLFW.*;

public class PlortWindow implements AutoCloseable {
    private final PlortInstance instance;
    private final long handle, surface;

    private int width, height;

    private final PlortInput input;

    public PlortWindow(PlortInstance instance, String title, int width, int height) {
        this.instance = instance;
        this.width = Math.max(width, 320); this.height = Math.max(height, 180);
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        this.handle = glfwCreateWindow(this.width, this.height, title, 0, 0);

        this.input = new PlortInput(this);

        glfwSetWindowSizeLimits(this.handle, 320, 180, GLFW_DONT_CARE, GLFW_DONT_CARE);

        GLFWWindowSizeCallback prevCallback = glfwSetWindowSizeCallback(handle, (window, w, h) -> {
            if (window != this.handle) return;
            this.width = w;
            this.height = h;
        });
        if (prevCallback != null) prevCallback.close();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer ret = stack.callocLong(1);

            VkUtil.check(GLFWVulkan.glfwCreateWindowSurface(instance.handle(), handle, null, ret));
            surface = ret.get(0);
        }
    }

    public PlortInput input() {
        return input;
    }

    public long handle() {
        return handle;
    }

    public boolean minimized() {
        return glfwGetWindowAttrib(handle, GLFW_ICONIFIED) == GLFW_TRUE;
    }

    public void pollEvents() {
        glfwPollEvents();
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(handle);
    }

    public long surface() {
        return surface;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    @Override
    public void close() {
        KHRSurface.vkDestroySurfaceKHR(instance.handle(), surface, null);
        glfwDestroyWindow(handle);
    }
}
