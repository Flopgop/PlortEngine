package net.flamgop.plort.engine.math.val;

public value record Vector4f(float x, float y, float z, float w) {
    public static final int BYTES = 4 * Float.BYTES;

    public Vector4f() {
        this(0,0,0,0);
    }
    public Vector4f(float v) {
        this(v,v,v,v);
    }
}
