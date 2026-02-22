package net.flamgop.plort.engine.math.val;

import org.jetbrains.annotations.Contract;

public value record Vector3i(int x, int y, int z) {
    public static final int BYTES = 3 * Integer.BYTES;

    @Contract(pure = true)
    public Vector3i() {
        this(0,0,0);
    }

    @Contract(pure = true)
    public Vector3i(int v) {
        this(v,v,v);
    }
}
