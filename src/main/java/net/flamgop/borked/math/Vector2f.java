package net.flamgop.borked.math;

import org.jetbrains.annotations.ApiStatus;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class Vector2f {
    public static final int BYTES = 2 * Float.BYTES;
    private static final ValueLayout.OfFloat F32 = ValueLayout.JAVA_FLOAT;

    private final MemorySegment memory;

    public Vector2f(Arena arena) {
        this.memory = arena.allocate(BYTES);
    }

    public Vector2f(Arena arena, float x, float y) {
        this(arena);
        this.x(x);
        this.y(y);
    }

    public Vector2f(float x, float y) {
        this(Arena.ofAuto(), x, y);
    }

    public Vector2f(Vector2f other) {
        this(other.x(), other.y());
    }

    public Vector2f(float v) {
        this(v, v);
    }

    @ApiStatus.Internal // note: not bounds checked
    public float getUnsafe(int index) {
        return memory.get(F32, (long) index * Float.BYTES);
    }

    public float get(int index) {
        if (index < 0 || index >= 2) throw new IndexOutOfBoundsException(index);
        return getUnsafe(index);
    }

    @ApiStatus.Internal
    public void setUnsafe(int index, float value) {
        memory.set(F32, (long) index * Float.BYTES, value);
    }

    public void set(int index, float value) {
        if (index < 0 || index >= 2) throw new IndexOutOfBoundsException(index);
        setUnsafe(index, value);
    }

    public void x(float value) {
        setUnsafe(0, value);
    }

    public void y(float value) {
        setUnsafe(1, value);
    }

    public float x() {
        return getUnsafe(0);
    }

    public float y() {
        return getUnsafe(1);
    }
}
