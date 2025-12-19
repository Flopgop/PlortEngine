package net.flamgop.borked.math;

import org.jetbrains.annotations.ApiStatus;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class Quaternionf {
    public static final long BYTES = 4 * Float.BYTES;
    private static final ValueLayout.OfFloat F32 = ValueLayout.JAVA_FLOAT;

    private final MemorySegment memory;

    public Quaternionf(Arena arena) {
        this.memory = arena.allocate(BYTES);
    }

    public Quaternionf(Arena arena, float x, float y, float z, float w) {
        this(arena);
        this.x(x);
        this.y(y);
        this.z(z);
        this.w(w);
    }

    public Quaternionf(float x, float y, float z, float w) {
        this(Arena.ofAuto(), x, y, z, w);
    }

    @ApiStatus.Internal // note: not bounds checked
    public float getUnsafe(int index) {
        return memory.get(F32, (long) index * Float.BYTES);
    }

    public float get(int index) {
        if (index < 0 || index >= 4) throw new IndexOutOfBoundsException(index);
        return getUnsafe(index);
    }

    @ApiStatus.Internal // note: not bounds checked
    public void setUnsafe(int index, float value) {
        memory.set(F32, (long) index * Float.BYTES, value);
    }

    public void set(int index, float value) {
        if (index < 0 || index >= 4) throw new IndexOutOfBoundsException(index);
        setUnsafe(index, value);
    }

    public void x(float value) {
        setUnsafe(0, value);
    }

    public void y(float value) {
        setUnsafe(1, value);
    }

    public void z(float value) {
        setUnsafe(2, value);
    }

    public void w(float value) {
        setUnsafe(3, value);
    }

    public float x() {
        return getUnsafe(0);
    }

    public float y() {
        return getUnsafe(1);
    }

    public float z() {
        return getUnsafe(2);
    }

    public float w() {
        return getUnsafe(3);
    }

    public float normSquared() {
        float w = w(), x = x(), y = y(), z = z();
        return w * w + x * x + y * y + z * z;
    }

    public float norm() {
        return (float) Math.sqrt(normSquared());
    }

    public Quaternionf scale(float scale) {
        x(x() * scale);
        y(y() * scale);
        z(z() * scale);
        w(w() * scale);
        return this;
    }

    public Quaternionf normalize() {
        return this.scale(1f / norm());
    }
}
