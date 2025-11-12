package net.flamgop.borked.engine;

import net.flamgop.borked.engine.memory.TrackedCloseable;
import net.flamgop.borked.engine.pipeline.PlortDescriptorSet;
import net.flamgop.borked.engine.pipeline.PlortDescriptorSetPool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PlortBufferedDescriptorSetPool extends TrackedCloseable {

    private final PlortDescriptorSetPool descriptorSetPool;

    private final int setCount;
    private final int frameCount;
    private int frame;

    public PlortBufferedDescriptorSetPool(PlortDevice device, List<PlortDescriptorSet> sets, int bufferedCount) {
        super();
        this.setCount = sets.size();
        List<PlortDescriptorSet> realSets = new ArrayList<>();
        for (int i = 0; i < bufferedCount; i++) {
            realSets.addAll(sets);
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
