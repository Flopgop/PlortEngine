package net.flamgop.borked.renderer.memory;

import net.flamgop.borked.math.Matrix4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.Buffer;

public class MappedMemory implements AutoCloseable {

    private final PlortBuffer buffer;
    private final long initialPtr, size;
    private long ptr;

    MappedMemory(PlortBuffer buffer, long size) {
        this.buffer = buffer;
        this.size = size;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer dst = stack.callocPointer(1);
            buffer.map(dst);
            this.initialPtr = dst.get(0);
            this.ptr = dst.get(0);
        }
    }

    @SuppressWarnings("unused")
    public long pointer() {
        return ptr;
    }

    public void putMatrix4f(Matrix4f matrix) {
        matrix.getToAddress(ptr);
        incrementPtr(16 * Float.BYTES);
    }

    public void putFloat(float f) {
        MemoryUtil.memPutFloat(ptr, f);
        incrementPtr(Float.BYTES);
    }

    public void putInt(int i) {
        MemoryUtil.memPutInt(ptr, i);
        incrementPtr(Integer.BYTES);
    }

    public void putLong(long l) {
        MemoryUtil.memPutLong(ptr, l);
        incrementPtr(Long.BYTES);
    }

    public void copy(long src, long bytes) {
        MemoryUtil.memCopy(src, this.ptr, bytes);
        incrementPtr(bytes);
    }

    public void copy(Buffer src, long bytes) {
        MemoryUtil.memCopy(MemoryUtil.memAddress(src), ptr, bytes);
        incrementPtr(bytes);
    }

    public void copyEntireBuffer(Buffer src) {
        MemoryUtil.memCopy(MemoryUtil.memAddress(src), ptr, src.capacity());
        incrementPtr(src.capacity());
    }

    private void checkOverflow() {
        if (ptr > initialPtr + size) throw new IndexOutOfBoundsException("MappedMemory overflow");
    }

    public void incrementPtr(long amount) {
        ptr += amount;
        checkOverflow();
    }

    @Override
    public void close() {
        buffer.unmap();
        if (ptr < initialPtr + size) {
            System.err.printf("[MappedMemory] WARNING: Buffer underflow (ptr = 0x%016X, expected = 0x%016X, difference = %d)%n", ptr, initialPtr + size, (initialPtr + size) - ptr);
            new Throwable().printStackTrace(System.err);
        }
    }
}
