package net.flamgop.plort.engine.math;

public class Hashing {
    public static long pairKey(Object a, Object b) {
        long ha = System.identityHashCode(a);
        long hb = System.identityHashCode(b);
        return ha < hb ? (ha << 32) | hb : (hb << 32) | ha;
    }
}
