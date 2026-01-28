package net.flamgop.borked.math;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.readonly.QuatArg;
import org.jetbrains.annotations.ApiStatus;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class Quaternionf {
    public static final int BYTES = 4 * Float.BYTES;
    private static final ValueLayout.OfFloat F32 = ValueLayout.JAVA_FLOAT;

    private final MemorySegment memory;

    public static Quaternionf fromAngularVelocity(Vector3f velocity, float dt) {
        float rx = velocity.x() * dt;
        float ry = velocity.y() * dt;
        float rz = velocity.z() * dt;

        float thetaSq = rx * rx + ry * ry + rz * rz;

        Quaternionf dest = new Quaternionf();
        if (thetaSq > 1e-12f) {
            float theta = (float) Math.sqrt(thetaSq);
            float halfTheta = theta * 0.5f;
            float sinHalfTheta = (float) Math.sin(halfTheta);
            float cosHalfTheta = (float) Math.cos(halfTheta);

            float s = sinHalfTheta / theta;
            dest.x(rx * s);
            dest.y(ry * s);
            dest.z(rz * s);
            dest.w(cosHalfTheta);
        } else {
            dest.x(rx * 0.5f);
            dest.y(ry * 0.5f);
            dest.z(rz * 0.5f);
            dest.w(1.0f);
        }
        return dest;
    }

    public Quaternionf(Arena arena) {
        this.memory = arena.allocate(BYTES);
        this.identity();
    }

    public Quaternionf() {
        this(Arena.ofAuto());
    }

    public Quaternionf(Arena arena, QuatArg quat) {
        this(arena, quat.getX(), quat.getY(), quat.getZ(), quat.getW());
    }

    public Quaternionf(QuatArg quat) {
        this(Arena.ofAuto(), quat);
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

    public Quaternionf(Arena arena, Quaternionf q) {
        this(arena, q.x(), q.y(), q.z(), q.w());
    }

    public Quaternionf(Quaternionf q) {
        this(q.x(), q.y(), q.z(), q.w());
    }

    @ApiStatus.Internal // note: not bounds checked
    public float getUnsafe(int index) {
        return memory.get(F32, (long) index * Float.BYTES);
    }

    public float get(int index) {
        if (index < 0 || index >= 4) throw new IndexOutOfBoundsException(index);
        return getUnsafe(index);
    }

    public QuatArg toJoltQuat() {
        return new Quat(x(),y(),z(),w());
    }

    @ApiStatus.Internal // note: not bounds checked
    public void setUnsafe(int index, float value) {
        memory.set(F32, (long) index * Float.BYTES, value);
    }

    public void set(int index, float value) {
        if (index < 0 || index >= 4) throw new IndexOutOfBoundsException(index);
        setUnsafe(index, value);
    }

    public Quaternionf setFrom(Quaternionf f) {
        this.memory.copyFrom(f.memory);
        return this;
    }

    public Quaternionf setFrom(Quat q) {
        this.x(q.getX());
        this.y(q.getY());
        this.z(q.getZ());
        this.w(q.getW());
        return this;
    }

    public Quaternionf identity() {
        this.x(0);
        this.y(0);
        this.z(0);
        this.w(1);
        return this;
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

    public Quaternionf multiply(Quaternionf q) {
        float newW = Math.fma(this.w(), q.w(), -Math.fma(this.x(), q.x(), Math.fma(this.y(), q.y(), this.z() * q.z())));
        float newX = Math.fma(this.w(), q.x(), Math.fma(this.x(), q.w(), Math.fma(this.y(), q.z(), -this.z() * q.y())));
        float newY = Math.fma(this.w(), q.y(), Math.fma(-this.x(), q.z(), Math.fma(this.y(), q.w(), this.z() * q.x())));
        float newZ = Math.fma(this.w(), q.z(), Math.fma(this.x(), q.y(), Math.fma(-this.y(), q.x(), this.z() * q.w())));

        this.w(newW);
        this.x(newX);
        this.y(newY);
        this.z(newZ);
        return this;
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
