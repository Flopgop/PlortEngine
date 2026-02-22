package net.flamgop.plort.engine.math.val;

import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import net.flamgop.plort.engine.math.MathUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public value record Vector3f(float x, float y, float z) {
    public static final int BYTES = 3 * Float.BYTES;

    @Contract(pure = true)
    public Vector3f() {
        this(0,0,0);
    }

    @Contract(pure = true)
    public Vector3f(float v) {
        this(v,v,v);
    }

    @Contract(pure = true)
    public Vector3f(@NotNull RVec3 vec) {
        this((Float) vec.getX(), (Float) vec.getY(), (Float) vec.getZ());
    }

    @Contract(pure = true)
    public Vector3f(@NotNull Vec3 vec) {
        this(vec.getX(), vec.getY(), vec.getZ());
    }

    @Contract(pure = true, value = "-> new")
    public @NotNull Vec3 toJoltVec3() {
        return new Vec3(x,y,z);
    }

    @Contract(pure = true, value = "-> _")
    public float lengthSquared() {
        return x * x + y * y + z * z;
    }

    @Contract(pure = true, value = "-> _")
    public float length() {
        return (float) Math.sqrt(lengthSquared());
    }

    @Contract(pure = true, value = "-> _")
    public float magnitude() {
        return length();
    }

    @Contract(pure = true, value = "-> new")
    public @NotNull Vector3f normalize() {
        return scale(1f / length());
    }

    @Contract(pure = true, value = "_ -> new")
    public @NotNull Vector3f scale(float scale) {
        return new Vector3f(x * scale, y * scale, z * scale);
    }

    @Contract(pure = true, value = "_ -> new")
    public @NotNull Vector3f scale(@NotNull Vector3f scale) {
        return new Vector3f(x * scale.x, y * scale.y, z * scale.z);
    }

    @Contract(pure = true, value = "_ -> new")
    public @NotNull Vector3f cross(@NotNull Vector3f b) {
        return new Vector3f(y * b.z - z * b.y, z * b.x - x * b.z, x * b.y - y * b.x);
    }

    @Contract(pure = true, value = "_ -> new")
    public @NotNull Vector3f add(float value) {
        return new Vector3f(x + value, y + value, z + value);
    }

    @Contract(pure = true, value = "_, _, _ -> new")
    public @NotNull Vector3f add(float x, float y, float z) {
        return new Vector3f(this.x + x, this.y + y, this.z + z);
    }

    @Contract(pure = true, value = "_ -> new")
    public @NotNull Vector3f add(@NotNull Vector3f b) {
        return new Vector3f(x + b.x, y + b.y, z + b.z);
    }

    @Contract(pure = true, value = "_, _ -> new")
    public @NotNull Vector3f addScaled(@NotNull Vector3f b, float scale) {
        return new Vector3f(
                Math.fma(b.x, scale, this.x),
                Math.fma(b.y, scale, this.y),
                Math.fma(b.z, scale, this.z)
        );
    }

    @Contract(pure = true, value = "_ -> new")
    public @NotNull Vector3f subtract(float value) {
        return new Vector3f(x - value, y - value, z - value);
    }

    @Contract(pure = true, value = "_, _, _ -> new")
    public @NotNull Vector3f subtract(float x, float y, float z) {
        return new Vector3f(this.x - x, this.y - y, this.z - z);
    }

    @Contract(pure = true, value = "_ -> new")
    public @NotNull Vector3f subtract(@NotNull Vector3f b) {
        return new Vector3f(this.x - b.x, this.y - b.y, this.z - b.z);
    }

    @Contract(pure = true, value = "_, _, _ -> new")
    public @NotNull Vector3f lerp(@NotNull Vector3f other, float t, float dt) {
        return new Vector3f(
            MathUtil.lerpf(this.x, other.x, t, dt),
            MathUtil.lerpf(this.y, other.y, t, dt),
            MathUtil.lerpf(this.z, other.z, t, dt)
        );
    }

    @Contract(pure = true, value = "-> new")
    public @NotNull Vector3f negate() {
        return scale(-1f);
    }

    @Contract(pure = true, value = "_ -> _")
    public float dot(@NotNull Vector3f b) {
        return Math.fma(x, b.x, Math.fma(y, b.y, z * b.z));
    }

    @Contract(pure = true, value = "_ -> new")
    public @NotNull Vector3f min(@NotNull Vector3f other) {
        return new Vector3f(Math.min(x, other.x), Math.min(y, other.y), Math.min(z, other.z));
    }

    @Contract(pure = true, value = "_ -> new")
    public @NotNull Vector3f max(@NotNull Vector3f other) {
        return new Vector3f(Math.max(x, other.x), Math.max(y, other.y), Math.max(z, other.z));
    }

    @Contract(pure = true, value = "-> _")
    public float max() {
        return Math.max(x, Math.max(y, z));
    }

    @Contract(pure = true, value = "-> _")
    public float min() {
        return Math.min(x, Math.min(y, z));
    }

    @Contract(mutates = "io")
    public void getToAddress(long ptr) {
        MemorySegment segment = MemorySegment.ofAddress(ptr).reinterpret(BYTES);
        segment.set(ValueLayout.JAVA_FLOAT, 0, x);
        segment.set(ValueLayout.JAVA_FLOAT, Float.BYTES, y);
        segment.set(ValueLayout.JAVA_FLOAT, 2 * Float.BYTES, z);
    }
}
