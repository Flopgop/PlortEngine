package net.flamgop.borked.renderer.descriptor;

import net.flamgop.borked.renderer.PlortDevice;
import net.flamgop.borked.renderer.memory.TrackedCloseable;
import net.flamgop.borked.renderer.util.VkUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.*;

import static org.lwjgl.vulkan.EXTDebugUtils.vkSetDebugUtilsObjectNameEXT;
import static org.lwjgl.vulkan.VK14.*;

public class PlortDescriptorSetPool extends TrackedCloseable {
    private final PlortDevice device;
    private final PlortDescriptorSetLayout[] layouts;
    private final long handle;
    private final long[] sets;

    @SuppressWarnings("resource")
    public PlortDescriptorSetPool(PlortDevice device, PlortDescriptorSetLayout... layouts) {
        super();
        this.device = device;
        this.layouts = layouts;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pOut = stack.callocLong(1);

            Map<PlortDescriptor.Type, Integer> typeCounts = new HashMap<>();
            for (PlortDescriptorSetLayout layout : layouts) {
                for (var entry : layout.descriptorCounts().entrySet()) {
                    typeCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
            }

            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(typeCounts.size(), stack);
            int idx = 0;
            for (var entry : typeCounts.entrySet()) {
                poolSizes.get(idx++)
                        .type(entry.getKey().qualifier())
                        .descriptorCount(entry.getValue());
            }

            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .pPoolSizes(poolSizes)
                    .maxSets(layouts.length);

            VkUtil.check(vkCreateDescriptorPool(device.handle(), poolInfo, null, pOut));
            this.handle = pOut.get(0);

            VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default()
                    .descriptorPool(this.handle)
                    .pSetLayouts(stack.longs(Arrays.stream(this.layouts).mapToLong(PlortDescriptorSetLayout::handle).toArray()));

            this.sets = new long[layouts.length];
            LongBuffer pDescriptorSets = stack.callocLong(layouts.length);
            VkUtil.check(vkAllocateDescriptorSets(device.handle(), allocInfo, pDescriptorSets));
            for (int i = 0; i < layouts.length; i++) this.sets[i] = pDescriptorSets.get(i);
        }
    }

    public void label(String name) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDebugUtilsObjectNameInfoEXT nameInfo = VkDebugUtilsObjectNameInfoEXT.calloc(stack)
                    .sType$Default()
                    .objectType(VK_OBJECT_TYPE_DESCRIPTOR_POOL)
                    .objectHandle(this.handle)
                    .pObjectName(stack.UTF8(name + " Descriptor Set Pool"));

            vkSetDebugUtilsObjectNameEXT(this.device.handle(), nameInfo);
        }
    }

    public long handle() {
        return handle;
    }

    public PlortDescriptorSetLayout[] layouts() {
        return layouts.clone();
    }

    public long[] descriptorSets() {
        return sets.clone();
    }

    public long[] descriptorSetsNoCopy() {
        return sets;
    }

    public long descriptorSet(int index) {
        return sets[index];
    }

    @Override
    public void close() {
        vkDestroyDescriptorPool(device.handle(), handle, null);
        super.close();
    }
}
