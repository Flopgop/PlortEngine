package net.flamgop.borked.renderer.window;

import net.flamgop.borked.renderer.util.VkUtil;
import net.flamgop.borked.renderer.util.VulkanException;
import org.lwjgl.glfw.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.windows.WindowsLibrary;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static org.lwjgl.glfw.GLFW.*;

public class PlortWindow implements AutoCloseable {
    private final VkInstance instance;
    private final long handle, surface;

    private int width, height;

    private final PlortInput input;

    public PlortWindow(VkInstance instance, String title, int width, int height) {
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

        int platform = glfwGetPlatform();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer ret = stack.callocLong(1);
            surface = switch (platform) {
                case GLFW_PLATFORM_WIN32 -> {
                    VkWin32SurfaceCreateInfoKHR createInfo = VkWin32SurfaceCreateInfoKHR.calloc(stack)
                            .sType$Default()
                            .hwnd(GLFWNativeWin32.glfwGetWin32Window(handle))
                            .hinstance(WindowsLibrary.HINSTANCE);
                    VkUtil.check(KHRWin32Surface.vkCreateWin32SurfaceKHR(instance, createInfo, null, ret));
                    yield ret.get(0);
                }
                case GLFW_PLATFORM_X11 -> {
                    VkXlibSurfaceCreateInfoKHR createInfo = VkXlibSurfaceCreateInfoKHR.calloc(stack)
                            .sType$Default()
                            .window(GLFWNativeX11.glfwGetX11Window(handle));
                    VkUtil.check(KHRXlibSurface.vkCreateXlibSurfaceKHR(instance, createInfo, null, ret));
                    yield ret.get(0);
                }
                case GLFW_PLATFORM_WAYLAND -> {
                    VkWaylandSurfaceCreateInfoKHR createInfo = VkWaylandSurfaceCreateInfoKHR.calloc(stack)
                            .sType$Default()
                            .surface(GLFWNativeWayland.glfwGetWaylandWindow(handle))
                            .display(GLFWNativeWayland.glfwGetWaylandDisplay());
                    VkUtil.check(KHRWaylandSurface.vkCreateWaylandSurfaceKHR(instance, createInfo, null, ret));
                    yield ret.get(0);
                }
                default -> throw new VulkanException("Unsupported platform!");
            };
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
        KHRSurface.vkDestroySurfaceKHR(instance, surface, null);
        glfwDestroyWindow(handle);
    }
}
