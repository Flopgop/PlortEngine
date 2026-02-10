package net.flamgop.plort.engine.math.val;

public value record Vector2i(int x, int y) {
    public static final int BYTES = 2 * Integer.BYTES;

    public Vector2i() {
        this(0,0);
    }
    public Vector2i(int v) {
        this(v,v);
    }
}
