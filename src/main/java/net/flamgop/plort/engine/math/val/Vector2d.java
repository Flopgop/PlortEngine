package net.flamgop.plort.engine.math.val;

public value record Vector2d(double x, double y) {
    public static final int BYTES = 2 * Double.BYTES;

    public Vector2d() {
        this(0,0);
    }
    public Vector2d(double v) {
        this(v,v);
    }
}
