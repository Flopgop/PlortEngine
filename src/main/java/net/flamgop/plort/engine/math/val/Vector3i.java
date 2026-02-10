package net.flamgop.plort.engine.math.val;

public value record Vector3i(int x, int y, int z) {
    public static final int BYTES = 3 * Integer.BYTES;

    public Vector3i() {
        this(0,0,0);
    }
    public Vector3i(int v) {
        this(v,v,v);
    }
}
