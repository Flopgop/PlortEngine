package net.flamgop.plort.engine.math.val;

import org.jetbrains.annotations.Contract;

public value record Vector4f(float x, float y, float z, float w) {
    public static final int BYTES = 4 * Float.BYTES;

    @Contract(pure = true)
    public Vector4f() {
        this(0,0,0,0);
    }

    @Contract(pure = true)
    public Vector4f(float v) {
        this(v,v,v,v);
    }
}
