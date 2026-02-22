package net.flamgop.plort.engine.math;

import org.jetbrains.annotations.Contract;

public class Hashing {
    @Contract(pure = true, value = "_, _ -> _")
    public static long pairKey(Object a, Object b) {
        long ha = System.identityHashCode(a);
        long hb = System.identityHashCode(b);
        return ha < hb ? (ha << 32) | hb : (hb << 32) | ha;
    }
}
