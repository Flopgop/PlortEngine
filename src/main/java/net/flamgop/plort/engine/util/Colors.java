package net.flamgop.plort.engine.util;

import net.flamgop.plort.engine.math.val.Vector3f;

public final class Colors {
    private static final Vector3f RED = new Vector3f(1.0f, 0.0f, 0.0f);
    private static final Vector3f GREEN = new Vector3f(0.0f, 1.0f, 0.0f);
    private static final Vector3f BLUE = new Vector3f(0.0f, 0.0f, 1.0f);
    private static final Vector3f YELLOW = new Vector3f(1.0f, 1.0f, 0.0f);
    private static final Vector3f CYAN = new Vector3f(0.0f, 1.0f, 1.0f);
    private static final Vector3f MAGENTA = new Vector3f(1.0f, 0.0f, 1.0f);
    private static final Vector3f BLACK = new Vector3f(0.0f);
    private static final Vector3f WHITE = new Vector3f(1.0f);

    public static Vector3f red() {
        return RED;
    }

    public static Vector3f green() {
        return GREEN;
    }

    public static Vector3f blue() {
        return BLUE;
    }

    public static Vector3f yellow() {
        return YELLOW;
    }

    public static Vector3f cyan() {
        return CYAN;
    }

    public static Vector3f magenta() {
        return MAGENTA;
    }

    public static Vector3f black() {
        return BLACK;
    }

    public static Vector3f white() {
        return WHITE;
    }
}
