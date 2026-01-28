package net.flamgop.borked.math;

import java.lang.foreign.Arena;

public record Frustum(Plane[] planes) {
    public static final int LEFT = 0, RIGHT = 1, BOTTOM = 2, TOP = 3, NEAR = 4, FAR = 5;

    public static Frustum fromViewProjectionMatrix(Matrix4f m) {
        return fromViewProjectionMatrix(Arena.ofAuto(), m);
    }

    public static Frustum fromViewProjectionMatrix(Arena arena, Matrix4f m) {
        Plane[] planes = new Plane[6];
        planes[LEFT]    = Plane.normalized(arena, m.m03() + m.m00(), m.m13() + m.m10(), m.m23() + m.m20(), m.m33() + m.m30());
        planes[RIGHT]   = Plane.normalized(arena, m.m03() - m.m00(), m.m13() - m.m10(), m.m23() - m.m20(), m.m33() - m.m30());
        planes[BOTTOM]  = Plane.normalized(arena, m.m03() + m.m01(), m.m13() + m.m11(), m.m23() + m.m21(), m.m33() + m.m31());
        planes[TOP]     = Plane.normalized(arena, m.m03() - m.m01(), m.m13() - m.m11(), m.m23() - m.m21(), m.m33() - m.m31());
        planes[NEAR]    = Plane.normalized(arena, m.m03() + m.m02(), m.m13() + m.m12(), m.m23() + m.m22(), m.m33() + m.m32());
        planes[FAR]     = Plane.normalized(arena, m.m03() - m.m02(), m.m13() - m.m12(), m.m23() - m.m22(), m.m33() - m.m32());

        return new Frustum(planes);
    }

    public boolean intersects(AABB aabb) {
        try (Arena arena = Arena.ofConfined()) {
            return intersects(arena, aabb);
        }
    }

    public boolean intersects(Arena arena, AABB aabb) {
        Vector3f temp = new Vector3f(arena);
        for (Plane plane : planes) {
            float px = plane.normal().x() > 0 ? aabb.max().x() : aabb.min().x();
            float py = plane.normal().y() > 0 ? aabb.max().y() : aabb.min().y();
            float pz = plane.normal().z() > 0 ? aabb.max().z() : aabb.min().z();

            if (plane.distanceToPoint(temp.set(px,py,pz)) < 0) {
                return false;
            }
        }
        return true;
    }
}
