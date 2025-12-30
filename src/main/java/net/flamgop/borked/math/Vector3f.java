package net.flamgop.borked.math;

import org.jetbrains.annotations.ApiStatus;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class Vector3f {
    public static final int BYTES = 3 * Float.BYTES;
    private static final ValueLayout.OfFloat F32 = ValueLayout.JAVA_FLOAT;

    private final MemorySegment memory;

    public Vector3f(Arena arena) {
        this.memory = arena.allocate(BYTES);
    }

    public Vector3f() {
        this(Arena.ofAuto());
    }

    public Vector3f(Arena arena, float x, float y, float z) {
        this(arena);
        this.x(x);
        this.y(y);
        this.z(z);
    }

    public Vector3f(float x, float y, float z) {
        this(Arena.ofAuto(), x, y, z);
    }

    public Vector3f(Arena arena, Vector3f vector3f) {
        this(arena, vector3f.x(), vector3f.y(), vector3f.z());
    }

    public Vector3f(Vector3f vector3f) {
        this(Arena.ofAuto(), vector3f);
    }

    public Vector3f(float v) {
        this(v, v, v);
    }

    @ApiStatus.Internal // note: not bounds checked
    public float getUnsafe(int index) {
        return memory.get(F32, (long) index * Float.BYTES);
    }

    public float get(int index) {
        if (index < 0 || index >= 3) throw new IndexOutOfBoundsException(index);
        return getUnsafe(index);
    }

    @ApiStatus.Internal
    public void setUnsafe(int index, float value) {
        memory.set(F32, (long) index * Float.BYTES, value);
    }

    public void set(int index, float value) {
        if (index < 0 || index >= 3) throw new IndexOutOfBoundsException(index);
        setUnsafe(index, value);
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

    public void x(float value) {
        setUnsafe(0, value);
    }

    public void y(float value) {
        setUnsafe(1, value);
    }

    public void z(float value) {
        setUnsafe(2, value);
    }

    public float lengthSquared() {
        float x = x();
        float y = y();
        float z = z();
        return x * x + y * y + z * z;
    }

    public float length() {
        return (float) Math.sqrt(lengthSquared());
    }

    public Vector3f normalize() {
        return this.scale(1f / length());
    }

    public Vector3f scale(float scale) {
        this.x(this.x() * scale);
        this.y(this.y() * scale);
        this.z(this.z() * scale);
        return this;
    }

    public Vector3f cross(Vector3f b) {
        float a1 = this.x();
        float a2 = this.y();
        float a3 = this.z();
        float b1 = b.x();
        float b2 = b.y();
        float b3 = b.z();

        this.x(a2*b3-a3*b2);
        this.y(a3*b1-a1*b3);
        this.z(a1*b2-a2*b1);
        return this;
    }

    public Vector3f add(float value) {
        this.x(this.x() + value);
        this.y(this.y() + value);
        this.z(this.z() + value);
        return this;
    }

    public Vector3f add(Vector3f b) {
        this.x(this.x() + b.x());
        this.y(this.y() + b.y());
        this.z(this.z() + b.z());
        return this;
    }

    public Vector3f subtract(float value) {
        this.x(this.x() - value);
        this.y(this.y() - value);
        this.z(this.z() - value);
        return this;
    }

    public Vector3f subtract(Vector3f b) {
        this.x(this.x() - b.x());
        this.y(this.y() - b.y());
        this.z(this.z() - b.z());
        return this;
    }

    public Vector3f negate() {
        return this.scale(-1f);
    }

    public float dot(Vector3f b) {
        return Math.fma(x(), b.x(), Math.fma(y(), b.y(), z() * b.z()));
    }
}
