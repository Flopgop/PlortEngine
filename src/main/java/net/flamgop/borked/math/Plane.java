package net.flamgop.borked.math;

import java.lang.foreign.Arena;

public record Plane(Vector3f normal, float distance) {
    public static Plane normalized(float a, float b, float c, float d) {
        return normalized(Arena.ofAuto(), a, b, c, d);
    }

    public static Plane normalized(Arena arena, float a, float b, float c, float d) {
        float length = (float) Math.sqrt(a * a + b * b + c * c);
        return new Plane(
                new Vector3f(arena, a / length, b / length, c / length),
                d / length
        );
    }

    public float distanceToPoint(Vector3f p) {
        return normal.dot(p) + distance;
    }
}
