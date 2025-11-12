package net.flamgop.borked;

import org.joml.Vector3f;

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
        return new Vector3f(RED);
    }

    public static Vector3f green() {
        return new Vector3f(GREEN);
    }

    public static Vector3f blue() {
        return new Vector3f(BLUE);
    }

    public static Vector3f yellow() {
        return new Vector3f(YELLOW);
    }

    public static Vector3f cyan() {
        return new Vector3f(CYAN);
    }

    public static Vector3f magenta() {
        return new Vector3f(MAGENTA);
    }

    public static Vector3f black() {
        return new Vector3f(BLACK);
    }

    public static Vector3f white() {
        return new Vector3f(WHITE);
    }
}
