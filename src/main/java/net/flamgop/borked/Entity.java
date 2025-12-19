package net.flamgop.borked;

import net.flamgop.borked.math.Matrix4f;
import net.flamgop.borked.math.Quaternionf;
import net.flamgop.borked.math.Vector3f;
import net.flamgop.borked.renderer.memory.BufferUsage;
import net.flamgop.borked.renderer.memory.MappedMemory;
import net.flamgop.borked.renderer.memory.PlortAllocator;
import net.flamgop.borked.renderer.memory.PlortBuffer;
import net.flamgop.borked.renderer.model.PlortModel;
import net.flamgop.borked.renderer.pipeline.PlortPipeline;
import org.lwjgl.vulkan.VkCommandBuffer;

public class Entity implements AutoCloseable {
    private static final int INSTANCE_BUFFER_SIZE = 2 * (4 * 4 * Float.BYTES); // model, inverse_model

    private final PlortModel model;
    private final PlortBuffer instanceBuffer;

    private final Matrix4f transform = new Matrix4f();
    private boolean transformDirty = true;

    public Entity(PlortModel model, PlortAllocator allocator) {
        this.model = model;
        this.instanceBuffer = new PlortBuffer(INSTANCE_BUFFER_SIZE, BufferUsage.STORAGE_BUFFER_BIT, allocator);
    }

    public void uploadTransform() {
        try (MappedMemory mem = this.instanceBuffer.map()) {
            mem.putMatrix4f(transform);
            mem.putMatrix4f(new Matrix4f(transform).invert());
        }
    }

    public void setPosition(Vector3f position) {
        this.setPosition(position.x(), position.y(), position.z());
    }
    public void setPosition(float x, float y, float z) {
        transform.translation(x, y, z);
        transformDirty = true;
    }

    public void rotation(Quaternionf quaternionf) {
        transform.rotation(quaternionf);
        transformDirty = true;
    }

    public PlortModel model() {
        return model;
    }

    public void submit(VkCommandBuffer cmdBuffer, PlortPipeline pipeline, int currentFrameModInFlight) {
        if (transformDirty) {
            uploadTransform();
            transformDirty = false;
        }
        model.submit(cmdBuffer, pipeline, instanceBuffer, 1, currentFrameModInFlight);
    }

    @Override
    public void close() {
        instanceBuffer.close();
        model.close();
    }
}
