package net.flamgop.borked.renderer.pipeline;

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
    private final long handle;
    private final long[] layouts, sets;

    @SuppressWarnings("resource")
    public PlortDescriptorSetPool(PlortDevice device, List<PlortDescriptorSet> descriptorSets) {
        super();
        this.device = device;
        this.layouts = new long[descriptorSets.size()];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pOut = stack.callocLong(1);

            Map<Integer, Integer> typeCounts = new HashMap<>();
            for (int i = 0; i < descriptorSets.size(); i++) {
                PlortDescriptorSet descriptorSet = descriptorSets.get(i);
                try (MemoryStack stack1 = MemoryStack.stackPush()) {
                    VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(descriptorSet.descriptors().length, stack1);
                    for (int j = 0; j < descriptorSet.descriptors().length; j++) {
                        PlortDescriptor descriptor = descriptorSet.descriptors()[j];
                        int type = descriptor.type().qualifier();
                        typeCounts.merge(type, descriptor.count(), Integer::sum);
                        bindings.get(j)
                                .binding(j)
                                .descriptorType(descriptor.type().qualifier())
                                .descriptorCount(descriptor.count())
                                .stageFlags(descriptor.stageFlags());
                    }

                    VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack1)
                            .sType$Default()
                            .pBindings(bindings);

                    VkUtil.check(vkCreateDescriptorSetLayout(device.handle(), layoutInfo, null, pOut));
                    this.layouts[i] = pOut.get(0);
                }
            }

            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(typeCounts.size(), stack);
            int idx = 0;
            for (var entry : typeCounts.entrySet()) {
                poolSizes.get(idx++)
                        .type(entry.getKey())
                        .descriptorCount(entry.getValue());
            }

            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .pPoolSizes(poolSizes)
                    .maxSets(descriptorSets.size());

            VkUtil.check(vkCreateDescriptorPool(device.handle(), poolInfo, null, pOut));
            this.handle = pOut.get(0);

            VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default()
                    .descriptorPool(this.handle)
                    .pSetLayouts(stack.longs(this.layouts));

            this.sets = new long[descriptorSets.size()];
            LongBuffer pDescriptorSets = stack.callocLong(descriptorSets.size());
            VkUtil.check(vkAllocateDescriptorSets(device.handle(), allocInfo, pDescriptorSets));
            for (int i = 0; i < descriptorSets.size(); i++) this.sets[i] = pDescriptorSets.get(i);
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

    public long[] layouts() {
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
        for (long layout : layouts) vkDestroyDescriptorSetLayout(device.handle(), layout, null);
        vkDestroyDescriptorPool(device.handle(), handle, null);
        super.close();
    }
}
