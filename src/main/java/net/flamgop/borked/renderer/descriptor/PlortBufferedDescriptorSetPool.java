package net.flamgop.borked.renderer.descriptor;

import net.flamgop.borked.renderer.PlortDevice;
import net.flamgop.borked.renderer.memory.TrackedCloseable;

import java.util.Arrays;

public class PlortBufferedDescriptorSetPool extends TrackedCloseable {

    private final PlortDescriptorSetPool descriptorSetPool;

    private final int setCount;
    private final int frameCount;
    private int frame;

    public PlortBufferedDescriptorSetPool(PlortDevice device, PlortDescriptorSetLayout layout, int setCount, int bufferedCount) {
        super();
        this.setCount = setCount;
        PlortDescriptorSetLayout[] realSets = new PlortDescriptorSetLayout[bufferedCount * setCount];
        for (int i = 0; i < bufferedCount; i++) {
            for (int j = 0; j < setCount; j++) {
                realSets[i * setCount + j] = layout;
            }
        }
        this.frameCount = bufferedCount;

        descriptorSetPool = new PlortDescriptorSetPool(device, realSets);
    }

    public long descriptorSet(int index) {
        int frame = this.frame % frameCount;
        return descriptorSet(frame, index);
    }

    public long descriptorSet(int frame, int index) {
        return descriptorSetPool.descriptorSet(frame * setCount + index);
    }

    public long[] descriptorSets(int frame) {
        return Arrays.copyOfRange(descriptorSetPool.descriptorSetsNoCopy(), frame * setCount, frame * setCount + setCount);
    }

    public void label(String name) {
        this.pool().label(name);
    }

    public long[] currentDescriptorSets() {
        return descriptorSets(frame);
    }

    public long[] allDescriptorSets() {
        return descriptorSetPool.descriptorSets();
    }

    public void incrementFrame() {
        this.frame++;
    }

    public PlortDescriptorSetPool pool() {
        return this.descriptorSetPool;
    }

    @Override
    public void close() {
        this.descriptorSetPool.close();
        super.close();
    }
}
