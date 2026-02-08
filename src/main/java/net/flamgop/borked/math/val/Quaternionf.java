package net.flamgop.borked.math.val;

import com.github.stephengold.joltjni.Quat;
import org.jetbrains.annotations.Contract;

public value record Quaternionf(float x, float y, float z, float w) {
    public static final int BYTES = 4 * Float.BYTES;

    public Quaternionf() {
        this(0,0,0,1);
    }

    public Quaternionf(Quat quat) {
        this(quat.getX(), quat.getY(), quat.getZ(), quat.getW());
    }

    @Contract(pure = true)
    public Quat toJoltQuat() {
        return new Quat(x,y,z,w);
    }

    @Contract(pure = true)
    public Quaternionf multiply(Quaternionf q) {
        return new Quaternionf(
                Math.fma(this.w, q.w, -Math.fma(this.x, q.x, Math.fma(this.y, q.y, this.z * q.z))),
                Math.fma(this.w, q.x, Math.fma(this.x, q.w, Math.fma(this.y, q.z, -this.z * q.y))),
                Math.fma(this.w, q.y, Math.fma(-this.x, q.z, Math.fma(this.y, q.w, this.z * q.x))),
                Math.fma(this.w, q.z, Math.fma(this.x, q.y, Math.fma(-this.y, q.x, this.z * q.w)))
        );
    }

    @Contract(pure = true)
    public float normSquared() {
        return w * w + x * x + y * y + z * z;
    }

    @Contract(pure = true)
    public float norm() {
        return (float) Math.sqrt(normSquared());
    }

    @Contract(pure = true)
    public Quaternionf scale(float scale) {
        return new Quaternionf(x*scale, y*scale, z*scale, w*scale);
    }

    @Contract(pure = true)
    public Quaternionf normalize() {
        return scale(1f / norm());
    }
}
