package net.flamgop.borked.math;

import org.jetbrains.annotations.ApiStatus;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class Vector2d {
    public static final int BYTES = 2 * Double.BYTES;
    private static final ValueLayout.OfDouble F64 = ValueLayout.JAVA_DOUBLE;

    private final MemorySegment memory;

    public Vector2d(Arena arena) {
        this.memory = arena.allocate(BYTES);
    }

    public Vector2d(Arena arena, double x, double y) {
        this(arena);
        this.x(x);
        this.y(y);
    }

    public Vector2d(double x, double y) {
        this(Arena.ofAuto(), x, y);
    }

    public Vector2d(Vector2d other) {
        this(other.x(), other.y());
    }

    public Vector2d(double v) {
        this(v, v);
    }

    @ApiStatus.Internal // note: not bounds checked
    public double getUnsafe(int index) {
        return memory.get(F64, (long) index * Double.BYTES);
    }

    public double get(int index) {
        if (index < 0 || index >= 2) throw new IndexOutOfBoundsException(index);
        return getUnsafe(index);
    }

    @ApiStatus.Internal
    public void setUnsafe(int index, double value) {
        memory.set(F64, (long) index * Double.BYTES, value);
    }

    public void set(int index, double value) {
        if (index < 0 || index >= 2) throw new IndexOutOfBoundsException(index);
        setUnsafe(index, value);
    }

    public void x(double value) {
        setUnsafe(0, value);
    }

    public void y(double value) {
        setUnsafe(1, value);
    }

    public double x() {
        return getUnsafe(0);
    }

    public double y() {
        return getUnsafe(1);
    }
}

