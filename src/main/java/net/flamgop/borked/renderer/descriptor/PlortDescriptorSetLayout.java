package net.flamgop.borked.renderer.descriptor;

import net.flamgop.borked.renderer.PlortDevice;
import net.flamgop.borked.renderer.memory.TrackedCloseable;
import net.flamgop.borked.renderer.util.VkUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;

import java.nio.LongBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.*;

public class PlortDescriptorSetLayout extends TrackedCloseable {
    private final PlortDevice device;
    private final Map<PlortDescriptor.Type, Integer> descriptorCounts = new HashMap<>();
    private final long handle;

    @SuppressWarnings("resource")
    public PlortDescriptorSetLayout(PlortDevice device, PlortDescriptor... descriptors) {
        super();
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(descriptors.length, stack);
            for (int i = 0; i < descriptors.length; i++) {
                PlortDescriptor descriptor = descriptors[i];
                descriptorCounts.merge(descriptor.type(), descriptor.count(), Integer::sum);
                bindings.get(i)
                        .binding(i)
                        .descriptorType(descriptor.type().qualifier())
                        .descriptorCount(descriptor.count())
                        .stageFlags(descriptor.stageFlags());
            }

            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pBindings(bindings);

            LongBuffer pOut = stack.callocLong(1);
            VkUtil.check(vkCreateDescriptorSetLayout(device.handle(), layoutInfo, null, pOut));
            this.handle = pOut.get(0);
        }
    }

    public long handle() {
        return handle;
    }

    public Map<PlortDescriptor.Type, Integer> descriptorCounts() {
        return Collections.unmodifiableMap(descriptorCounts);
    }

    @Override
    public void close() {
        super.close();
        vkDestroyDescriptorSetLayout(device.handle(), handle, null);
    }
}
