package net.flamgop.plort.engine.renderer.debug;

import net.flamgop.plort.engine.renderer.PlortInstance;
import net.flamgop.plort.engine.renderer.memory.TrackedCloseable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCallbackDataEXT;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCreateInfoEXT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.LongBuffer;
import java.util.EnumSet;

import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.VK10.VK_FALSE;

public class PlortDebugMessenger extends TrackedCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlortDebugMessenger.class);

    private final PlortInstance instance;
    private final long handle;

    public PlortDebugMessenger(PlortInstance instance, EnumSet<MessageSeverity> severities, EnumSet<MessageType> types) {
        super();
        this.instance = instance;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int severityMask = severities.stream().mapToInt(MessageSeverity::qualifier).reduce((i1,i2) -> i1 | i2).orElse(0);
            int typeMask = types.stream().mapToInt(MessageType::qualifier).reduce((i1,i2) -> i1 | i2).orElse(0);

            VkDebugUtilsMessengerCreateInfoEXT debugInfo = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack)
                    .sType$Default()
                    .messageSeverity(severityMask)
                    .messageType(typeMask)
                    .pfnUserCallback(this::printDebugOutput);

            LongBuffer pMessenger = stack.callocLong(1);
            vkCreateDebugUtilsMessengerEXT(instance.handle(), debugInfo, null, pMessenger);
            this.handle = pMessenger.get(0);
        }
    }

    private int printDebugOutput(int messageSeverity, int messageTypes, long pCallbackData, long pUserData) {
        String type = "";
        if ((messageTypes & VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT) != 0) type += "GENERAL ";
        if ((messageTypes & VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT) != 0) type += "VALIDATION ";
        if ((messageTypes & VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT) != 0) type += "PERFORMANCE ";

        @SuppressWarnings("resource")
        VkDebugUtilsMessengerCallbackDataEXT callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
        String message = callbackData.pMessageString();

        if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) {
            LOGGER.error("[{}] {}", type.trim(), message);
        } else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0) {
            LOGGER.warn("[{}] {}", type.trim(), message);
        } else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT) != 0) {
            LOGGER.info("[{}] {}", type.trim(), message);
        } else {
            LOGGER.debug("[{}] {}", type.trim(), message);
        }

        return VK_FALSE;
    }

    @Override
    public void close() {
        super.close();
        vkDestroyDebugUtilsMessengerEXT(instance.handle(), this.handle, null);
    }
}
