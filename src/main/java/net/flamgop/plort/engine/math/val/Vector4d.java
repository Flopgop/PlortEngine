package net.flamgop.plort.engine.math.val;

import org.jetbrains.annotations.Contract;

public value record Vector4d(double x, double y, double z, double w) {
    public static final int BYTES = 4 * Double.BYTES;

    @Contract(pure = true)
    public Vector4d() {
        this(0,0,0,0);
    }

    @Contract(pure = true)
    public Vector4d(float v) {
        this(v,v,v,v);
    }
}
