package net.flamgop.borked.math;

/// note: this is NOT immutable, but my editor wants it to be a record so it is.
public record AABB(Vector3f min, Vector3f max, boolean hasCollision) {

    public AABB(Vector3f min, Vector3f max) {
        this(min, max, true);
    }

    public AABB(AABB other) {
        this(new Vector3f(other.min()), new Vector3f(other.max()), other.hasCollision());
    }

    public Vector3f size() {
        return new Vector3f(max).subtract(min);
    }

    public Vector3f center() {
        return new Vector3f(min).add(max).scale(0.5f);
    }

    public AABB translated(Vector3f delta) {
        return new AABB(new Vector3f(min), new Vector3f(max)).translate(delta);
    }

    public AABB translate(Vector3f delta) {
        min.add(delta);
        max.add(delta);
        return this;
    }

    public boolean contains(Vector3f p) {
        return p.x() >= min.x() && p.x() <= max.x()
                && p.y() >= min.y() && p.y() <= max.y()
                && p.z() >= min.z() && p.z() <= max.z();
    }

    public boolean intersects(AABB other) {
        return max.x() > other.min.x() && min.x() < other.max.x()
                && max.y() > other.min.y() && min.y() < other.max.y()
                && max.z() > other.min.z() && min.z() < other.max.z();
    }

    public Vector3f penetration(AABB other) {
        Vector3f d1 = new Vector3f(other.max).subtract(min);
        Vector3f d2 = new Vector3f(max).subtract(other.min);

        float dx = Math.min(d1.x(), d2.x());
        float dy = Math.min(d1.y(), d2.y());
        float dz = Math.min(d1.z(), d2.z());

        return new Vector3f(dx, dy, dz);
    }

    public Vector3f resolve(AABB other) {
        Vector3f p = penetration(other);
        if (p.x() < p.y() && p.x() < p.z()) return new Vector3f(min.x() < other.min.x() ? -p.x() : p.x(), 0, 0);
        if (p.y() < p.z()) return new Vector3f(0, min.y() < other.min.y() ? -p.y() : p.y(), 0);
        return new Vector3f(0, 0, min.z() < other.min.z() ? -p.z() : p.z());
    }

    public Vector3f resolveAxis(AABB other, Axis axis) {
        Vector3f p = penetration(other);
        return switch (axis) {
            case X -> {
                float push = Math.min(p.x(), Math.abs(min.x() - other.min.x()));
                yield new Vector3f(min.x() < other.min.x() ? -push : push, 0, 0);
            }
            case Y -> {
                float push = Math.min(p.y(), Math.abs(min.y() - other.min.y()));
                yield new Vector3f(0, min.y() < other.min.y() ? -push : push, 0);
            }
            case Z -> {
                float push = Math.min(p.z(), Math.abs(min.z() - other.min.z()));
                yield new Vector3f(0, 0, min.z() < other.min.z() ? -push : push);
            }
        };
    }

    public AABB expanded(float x, float y, float z) {
        return new AABB(new Vector3f(min), new Vector3f(max)).expand(x, y, z);
    }

    public AABB expand(float x, float y, float z) {
        min.subtract(x, y, z);
        max.add(x, y, z);
        return this;
    }

    public AABB union(AABB other) {
        return new AABB(min.min(other.min), max.max(other.max));
    }
}
