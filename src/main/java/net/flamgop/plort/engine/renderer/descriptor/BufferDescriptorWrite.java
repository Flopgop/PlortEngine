package net.flamgop.plort.engine.renderer.descriptor;

import net.flamgop.plort.engine.renderer.memory.PlortBuffer;

import java.util.List;

public value record BufferDescriptorWrite(List<PlortBuffer> buffers, PlortDescriptor.Type type, int dstBinding, long dstSet) implements DescriptorWrite {
    @Override
    public int count() {
        return buffers.size();
    }

    @Override
    public boolean valid() {
        return  type == PlortDescriptor.Type.UNIFORM_BUFFER         ||
                type == PlortDescriptor.Type.STORAGE_BUFFER         ||
                type == PlortDescriptor.Type.UNIFORM_BUFFER_DYNAMIC ||
                type == PlortDescriptor.Type.STORAGE_BUFFER_DYNAMIC ||
                type == PlortDescriptor.Type.INLINE_UNIFORM_BLOCK;
    }
}
