package net.flamgop.borked.entity.components;

import net.flamgop.borked.math.val.Matrix4f;
import net.flamgop.borked.model.PlortModel;
import net.flamgop.borked.renderer.PlortDevice;
import net.flamgop.borked.renderer.descriptor.PlortBufferedDescriptorSetPool;
import net.flamgop.borked.renderer.memory.BufferUsage;
import net.flamgop.borked.renderer.memory.BufferedObject;
import net.flamgop.borked.renderer.memory.PlortAllocator;
import net.flamgop.borked.renderer.memory.PlortBuffer;

public record RenderInstance(BufferedObject<PlortBuffer> buffers, PlortBufferedDescriptorSetPool descriptorSetPool, PlortBufferedDescriptorSetPool shadowDescriptorSetPool) implements AutoCloseable {
    public static final int INSTANCE_BUFFER_SIZE = 2 * Matrix4f.BYTES;

    public RenderInstance(PlortDevice device, PlortAllocator allocator, PlortModel model, int numFramesInFlight) {
        this(
                new BufferedObject<>(PlortBuffer.class, numFramesInFlight, (_) -> new PlortBuffer(INSTANCE_BUFFER_SIZE, BufferUsage.STORAGE_BUFFER_BIT, allocator)),
                new PlortBufferedDescriptorSetPool(device, model.layout(), model.materialCount(), numFramesInFlight),
                new PlortBufferedDescriptorSetPool(device, model.layout(), model.materialCount(), numFramesInFlight)
        );
    }

    @Override
    public void close() {
        try { buffers.close(); } catch (Exception _) {}
        descriptorSetPool.close();
        shadowDescriptorSetPool.close();
    }
}
