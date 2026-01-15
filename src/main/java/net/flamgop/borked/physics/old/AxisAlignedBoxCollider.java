package net.flamgop.borked.physics.old;

import net.flamgop.borked.math.AABB;
import net.flamgop.borked.math.Matrix3f;
import net.flamgop.borked.math.Quaternionf;
import net.flamgop.borked.math.Vector3f;

import java.lang.foreign.Arena;

public record AxisAlignedBoxCollider(AABB aabb, Vector3f position, Quaternionf rotation, Vector3f linearVelocity, Vector3f angularVelocity, boolean dynamic, float mass, float restitution, float friction) implements Collider {
    public AxisAlignedBoxCollider(AABB aabb, boolean dynamic, float mass, float restitution, float friction) {
        this(new AABB(aabb), new Vector3f(), new Quaternionf(), new Vector3f(), new Vector3f(), dynamic, mass, restitution, friction);
    }

    @Override
    public void updateAABBtoMatchPosition() {
        Vector3f halfExtent = new Vector3f(aabb.max())
                .subtract(aabb.min())
                .scale(0.5f);

        aabb.min().setFrom(new Vector3f(position).subtract(halfExtent));
        aabb.max().setFrom(new Vector3f(position).add(halfExtent));
    }

    @Override
    public boolean resolveStatic(Arena arena, Collider other) {
        AABB b = other.aabb();
        if (!aabb.intersects(b)) return false;
        Vector3f resolution = aabb.resolve(arena, b);
        this.position().add(resolution);
        if (resolution.x() != 0) this.linearVelocity.x(0);
        else if (resolution.y() != 0) this.linearVelocity.y(0);
        else if (resolution.z() != 0) this.linearVelocity.z(0);
        return true;
    }

    @Override
    public boolean resolveDynamic(Arena arena, Collider other) {
        AABB b = other.aabb();
        if (!aabb.intersects(b)) return false;
        Vector3f resolution = aabb.resolve(arena, b);
        this.position().addScaled(resolution, 0.5f);
        if (resolution.x() != 0) this.linearVelocity.x(0);
        else if (resolution.y() != 0) this.linearVelocity.y(0);
        else if (resolution.z() != 0) this.linearVelocity.z(0);
        return true;
    }

    @Override
    public Matrix3f inverseInertiaTensorWorld() {
        Matrix3f invInertia = new Matrix3f().identity();

        if (!dynamic || mass <= 0.0f) {
            invInertia.identity();
            return invInertia;
        }

        float width  = aabb.max().x() - aabb.min().x();
        float height = aabb.max().y() - aabb.min().y();
        float depth  = aabb.max().z() - aabb.min().z();

        float w2 = width * width;
        float h2 = height * height;
        float d2 = depth * depth;

        float factor = 12.0f / mass;

        float invIxx = factor / (h2 + d2);
        float invIyy = factor / (w2 + d2);
        float invIzz = factor / (w2 + h2);

        invInertia.m00(invIxx);
        invInertia.m11(invIyy);
        invInertia.m22(invIzz);

        return invInertia;
    }
}
