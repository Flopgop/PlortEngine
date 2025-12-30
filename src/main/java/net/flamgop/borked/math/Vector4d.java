package net.flamgop.borked.math;

import org.jetbrains.annotations.ApiStatus;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class Vector4d {
    public static final int BYTES = 4 * Double.BYTES;
    private static final ValueLayout.OfDouble F64 = ValueLayout.JAVA_DOUBLE;

    private final MemorySegment memory;

    public Vector4d(Arena arena) {
        this.memory = arena.allocate(BYTES);
    }

    public Vector4d() {
        this(Arena.ofAuto());
    }

    public Vector4d(Arena arena, double x, double y, double z, double w) {
        this(arena);
        this.x(x);
        this.y(y);
        this.z(z);
        this.w(w);
    }

    public Vector4d(double x, double y, double z, double w) {
        this(Arena.ofAuto(), x, y, z, w);
    }

    public Vector4d(Vector4d other) {
        this(other.x(), other.y(), other.z(), other.w());
    }

    public Vector4d(double v) {
        this(v, v, v, v);
    }

    @ApiStatus.Internal // note: not bounds checked
    public double getUnsafe(int index) {
        return memory.get(F64, (long) index * Double.BYTES);
    }

    public double get(int index) {
        if (index < 0 || index >= 4) throw new IndexOutOfBoundsException(index);
        return getUnsafe(index);
    }

    @ApiStatus.Internal
    public void setUnsafe(int index, double value) {
        memory.set(F64, (long) index * Double.BYTES, value);
    }

    public void set(int index, double value) {
        if (index < 0 || index >= 4) throw new IndexOutOfBoundsException(index);
        setUnsafe(index, value);
    }

    public void x(double value) {
        setUnsafe(0, value);
    }

    public void y(double value) {
        setUnsafe(1, value);
    }

    public void z(double value) {
        setUnsafe(2, value);
    }

    public void w(double value) {
        setUnsafe(3, value);
    }

    public double x() {
        return getUnsafe(0);
    }

    public double y() {
        return getUnsafe(1);
    }

    public double z() {
        return getUnsafe(2);
    }

    public double w() {
        return getUnsafe(3);
    }
}
