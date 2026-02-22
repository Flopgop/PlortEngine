package net.flamgop.plort.engine.math.val;

import org.jetbrains.annotations.Contract;

public value record Vector2f(float x, float y) {
    public static final int BYTES = 2 * Float.BYTES;

    @Contract(pure = true)
    public Vector2f() {
        this(0,0);
    }

    @Contract(pure = true)
    public Vector2f(float v) {
        this(v,v);
    }
}
