package net.flamgop.plort.engine.math.val;

import org.jetbrains.annotations.Contract;

public value record Vector2d(double x, double y) {
    public static final int BYTES = 2 * Double.BYTES;

    @Contract(pure = true)
    public Vector2d() {
        this(0,0);
    }

    @Contract(pure = true)
    public Vector2d(double v) {
        this(v,v);
    }
}
