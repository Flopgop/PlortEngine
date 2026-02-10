package net.flamgop.plort.engine.math.val;

public value record Vector4d(double x, double y, double z, double w) {
    public static final int BYTES = 4 * Double.BYTES;

    public Vector4d() {
        this(0,0,0,0);
    }
    public Vector4d(float v) {
        this(v,v,v,v);
    }
}
