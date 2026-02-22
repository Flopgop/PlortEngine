package net.flamgop.plort.engine.math.val;

import org.jetbrains.annotations.Contract;

public value record Vector2i(int x, int y) {
    public static final int BYTES = 2 * Integer.BYTES;

    @Contract(pure = true)
    public Vector2i() {
        this(0,0);
    }

    @Contract(pure = true)
    public Vector2i(int v) {
        this(v,v);
    }
}
