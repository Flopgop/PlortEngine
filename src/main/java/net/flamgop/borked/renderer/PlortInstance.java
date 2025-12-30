package net.flamgop.borked.renderer;

import net.flamgop.borked.renderer.memory.TrackedCloseable;
import net.flamgop.borked.renderer.util.VkUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;

import java.util.List;

import static org.lwjgl.vulkan.VK10.vkCreateInstance;
import static org.lwjgl.vulkan.VK10.vkDestroyInstance;

public class PlortInstance extends TrackedCloseable {
    public static int makeVersion(int variant, int major, int minor, int patch) {
        return (variant << 29) | (major << 22) | (minor << 12) | patch;
    }

    private final VkInstance handle;

    public PlortInstance(int apiVersion, String engineName, int engineVersion, String appName, int appVersion, List<String> enabledLayerNames, List<String> enabledExtensionNames, long pNext, boolean includeGLFWExtensions) {
        super();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int requiredExtensionCount;
            if (includeGLFWExtensions) {
                PointerBuffer extensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
                if (extensions == null) throw new IllegalStateException("GLFW returned a null pointer for it's required instance extensions.");
                requiredExtensionCount = extensions.capacity();
            } else requiredExtensionCount = 0;

            PointerBuffer instanceExtensions = stack.callocPointer(enabledExtensionNames.size() + requiredExtensionCount);
            for (String ext : enabledExtensionNames) instanceExtensions.put(stack.UTF8(ext));
            if (includeGLFWExtensions) {
                PointerBuffer extensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
                if (extensions == null) throw new IllegalStateException("GLFW returned a null pointer for it's required instance extensions.");
                instanceExtensions.put(extensions);
            }
            instanceExtensions.flip();

            PointerBuffer validationLayers = stack.callocPointer(enabledLayerNames.size());
            for (String layer : enabledLayerNames) validationLayers.put(stack.UTF8(layer));
            validationLayers.flip();

            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                    .apiVersion(apiVersion)
                    .engineVersion(engineVersion)
                    .applicationVersion(appVersion)
                    .pEngineName(stack.UTF8(engineName))
                    .pApplicationName(stack.UTF8(appName));

            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pApplicationInfo(appInfo)
                    .ppEnabledExtensionNames(instanceExtensions)
                    .ppEnabledLayerNames(validationLayers)
                    .pNext(pNext);

            PointerBuffer ret = stack.callocPointer(1);
            VkUtil.check(vkCreateInstance(createInfo, null, ret));
            this.handle = new VkInstance(ret.get(), createInfo);
        }
    }

    public PlortInstance(int apiVersion, String engineName, int engineVersion, String appName, int appVersion, List<String> enabledLayerNames, List<String> enabledExtensionNames, long pNext) {
        this(apiVersion, engineName, engineVersion, appName, appVersion, enabledLayerNames, enabledExtensionNames, pNext, false);
    }

    public PlortInstance(int apiVersion, String engineName, int engineVersion, String appName, int appVersion, List<String> enabledLayerNames, List<String> enabledExtensionNames) {
        this(apiVersion, engineName, engineVersion, appName, appVersion, enabledLayerNames, enabledExtensionNames, 0, false);
    }

    public PlortInstance(int apiVersion, String engineName, int engineVersion, String appName, int appVersion, List<String> enabledLayerNames, List<String> enabledExtensionNames, boolean includeGLFWExtensions) {
        this(apiVersion, engineName, engineVersion, appName, appVersion, enabledLayerNames, enabledExtensionNames, 0, true);
    }

    public VkInstance handle() {
        return handle;
    }

    @Override
    public void close() {
        super.close();
        vkDestroyInstance(handle, null);
    }
}
