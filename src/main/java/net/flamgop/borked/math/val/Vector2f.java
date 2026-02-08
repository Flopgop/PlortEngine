package net.flamgop.borked.math.val;

public value record Vector2f(float x, float y) {
    public static final int BYTES = 2 * Float.BYTES;

    public Vector2f() {
        this(0,0);
    }
    public Vector2f(float v) {
        this(v,v);
    }
}
