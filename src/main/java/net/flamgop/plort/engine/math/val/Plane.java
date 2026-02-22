package net.flamgop.plort.engine.math.val;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public value record Plane(Vector3f normal, float distance) {
    @Contract(pure = true, value = "_, _, _, _ -> new")
    public static Plane normalized(float a, float b, float c, float d) {
        float length = (float) Math.sqrt(a * a + b * b + c * c);
        return new Plane(
                new Vector3f(a / length, b / length, c / length),
                d / length
        );
    }

    @Contract(pure = true, value = "_ -> _")
    public float distanceToPoint(@NotNull Vector3f p) {
        return normal.dot(p) + distance;
    }
}
