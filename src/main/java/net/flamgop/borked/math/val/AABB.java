package net.flamgop.borked.math.val;

import org.jetbrains.annotations.Contract;

public value record AABB(Vector3f min, Vector3f max) {
    public AABB(AABB other) {
        this(other.min, other.max);
    }

    @Contract(pure = true)
    public Vector3f size() {
        return max.subtract(min);
    }

    @Contract(pure = true)
    public Vector3f center() {
        return min.add(max).scale(0.5f);
    }

    @Contract(pure = true)
    public AABB translate(Vector3f delta) {
        return new AABB(min.add(delta), max.add(delta));
    }

    @Contract(pure = true)
    public boolean contains(Vector3f p) {
        return p.x() >= min.x() && p.x() <= max.x()
                && p.y() >= min.y() && p.y() <= max.y()
                && p.z() >= min.z() && p.z() <= max.z();
    }

    @Contract(pure = true)
    public boolean intersects(AABB other) {
        return max.x() > other.min.x() && min.x() < other.max.x()
                && max.y() > other.min.y() && min.y() < other.max.y()
                && max.z() > other.min.z() && min.z() < other.max.z();
    }

    @Contract(pure = true)
    public Vector3f penetration(AABB other) {
        Vector3f d1 = other.max.subtract(min);
        Vector3f d2 = max.subtract(other.min);

        float dx = Math.min(d1.x(), d2.x());
        float dy = Math.min(d1.y(), d2.y());
        float dz = Math.min(d1.z(), d2.z());

        return new Vector3f(dx, dy, dz);
    }

    @Contract(pure = true)
    public AABB expand(float x, float y, float z) {
        return new AABB(min.subtract(x,y,z), max.add(x,y,z));
    }

    @Contract(pure = true)
    public AABB union(AABB other) {
        return new AABB(min.min(other.min), max.max(other.max));
    }
}
