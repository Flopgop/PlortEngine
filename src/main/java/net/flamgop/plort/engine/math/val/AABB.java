package net.flamgop.plort.engine.math.val;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public value record AABB(@NotNull Vector3f min, @NotNull Vector3f max) {
    @Contract(pure = true)
    public AABB(@NotNull AABB other) {
        this(other.min, other.max);
    }

    @Contract(pure = true, value = "-> new")
    public @NotNull Vector3f size() {
        return max.subtract(min);
    }

    @Contract(pure = true, value = "-> new")
    public @NotNull Vector3f center() {
        return min.add(max).scale(0.5f);
    }

    @Contract(pure = true, value = "_ -> new")
    public @NotNull AABB translate(@NotNull Vector3f delta) {
        return new AABB(min.add(delta), max.add(delta));
    }

    @Contract(pure = true, value = "_ -> _")
    public boolean contains(@NotNull Vector3f p) {
        return p.x() >= min.x() && p.x() <= max.x()
                && p.y() >= min.y() && p.y() <= max.y()
                && p.z() >= min.z() && p.z() <= max.z();
    }

    @Contract(pure = true, value = "_ -> _")
    public boolean intersects(@NotNull AABB other) {
        return max.x() > other.min.x() && min.x() < other.max.x()
                && max.y() > other.min.y() && min.y() < other.max.y()
                && max.z() > other.min.z() && min.z() < other.max.z();
    }

    @Contract(pure = true, value = "_ -> new")
    public @NotNull Vector3f penetration(@NotNull AABB other) {
        Vector3f d1 = other.max.subtract(min);
        Vector3f d2 = max.subtract(other.min);

        float dx = Math.min(d1.x(), d2.x());
        float dy = Math.min(d1.y(), d2.y());
        float dz = Math.min(d1.z(), d2.z());

        return new Vector3f(dx, dy, dz);
    }

    @Contract(pure = true, value = "_, _, _ -> new")
    public @NotNull AABB expand(float x, float y, float z) {
        return new AABB(min.subtract(x,y,z), max.add(x,y,z));
    }

    @Contract(pure = true, value = "_ -> new")
    public @NotNull AABB union(@NotNull AABB other) {
        return new AABB(min.min(other.min), max.max(other.max));
    }
}
