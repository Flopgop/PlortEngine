package net.flamgop.plort.engine.math.val;

import com.github.stephengold.joltjni.Quat;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public value record Quaternionf(float x, float y, float z, float w) {
    public static final int BYTES = 4 * Float.BYTES;

    @Contract(pure = true)
    public Quaternionf() {
        this(0,0,0,1);
    }

    @Contract(pure = true)
    public Quaternionf(@NotNull Quat quat) {
        this(quat.getX(), quat.getY(), quat.getZ(), quat.getW());
    }

    @Contract(pure = true, value = "-> new")
    public @NotNull Quat toJoltQuat() {
        return new Quat(x,y,z,w);
    }

    @Contract(pure = true, value = "_ -> new")
    public @NotNull Quaternionf multiply(@NotNull Quaternionf q) {
        return new Quaternionf(
                Math.fma(this.w, q.w, -Math.fma(this.x, q.x, Math.fma(this.y, q.y, this.z * q.z))),
                Math.fma(this.w, q.x, Math.fma(this.x, q.w, Math.fma(this.y, q.z, -this.z * q.y))),
                Math.fma(this.w, q.y, Math.fma(-this.x, q.z, Math.fma(this.y, q.w, this.z * q.x))),
                Math.fma(this.w, q.z, Math.fma(this.x, q.y, Math.fma(-this.y, q.x, this.z * q.w)))
        );
    }

    @Contract(pure = true, value = "-> _")
    public float normSquared() {
        return w * w + x * x + y * y + z * z;
    }

    @Contract(pure = true, value = "-> _")
    public float norm() {
        return (float) Math.sqrt(normSquared());
    }

    @Contract(pure = true, value = "_ -> new")
    public @NotNull Quaternionf scale(float scale) {
        return new Quaternionf(x*scale, y*scale, z*scale, w*scale);
    }

    @Contract(pure = true, value = "-> new")
    public @NotNull Quaternionf normalize() {
        return scale(1f / norm());
    }
}
