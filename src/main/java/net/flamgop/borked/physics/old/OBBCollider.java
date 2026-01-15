package net.flamgop.borked.physics.old;

import net.flamgop.borked.math.AABB;
import net.flamgop.borked.math.Matrix3f;
import net.flamgop.borked.math.Quaternionf;
import net.flamgop.borked.math.Vector3f;

import java.lang.foreign.Arena;

public class OBBCollider implements Collider {
    private final OBB obb;
    private final AABB aabb = new AABB(new Vector3f(), new Vector3f());
    private final Vector3f linearVelocity = new Vector3f();
    private final Vector3f angularVelocity = new Vector3f();

    private final boolean dynamic;
    private final float mass;
    private final float restitution;
    private final float friction;

    public OBBCollider(Vector3f halfExtents, boolean dynamic, float mass, float restitution, float friction) {
        this.obb = new OBB(halfExtents);
        this.dynamic = dynamic;
        this.mass = mass;
        this.restitution = restitution;
        this.friction = friction;
        updateAABBtoMatchPosition();
    }

    public OBBCollider(AABB aabb, boolean dynamic, float mass, float restitution, float friction) {
        this.obb = new OBB(aabb);
        this.dynamic = dynamic;
        this.mass = mass;
        this.restitution = restitution;
        this.friction = friction;
        updateAABBtoMatchPosition();
    }

    @Override
    public boolean dynamic() {
        return dynamic;
    }

    @Override
    public float mass() {
        return mass;
    }

    @Override
    public float restitution() {
        return restitution;
    }

    @Override
    public float friction() {
        return friction;
    }

    @Override
    public Vector3f position() {
        return this.obb.position();
    }

    @Override
    public Quaternionf rotation() {
        return this.obb.rotation();
    }

    @Override
    public Vector3f linearVelocity() {
        return linearVelocity;
    }

    @Override
    public Vector3f angularVelocity() {
        return angularVelocity;
    }

    @Override
    public Matrix3f inverseInertiaTensorWorld() {
        if (!dynamic || mass <= 0.0f) {
            // Static or infinite-mass body
            return new Matrix3f();
        }

        Vector3f h = obb.halfExtents();
        float w2 = 4.0f * h.x() * h.x();
        float h2 = 4.0f * h.y() * h.y();
        float d2 = 4.0f * h.z() * h.z();

        float ix = (mass / 12.0f) * (h2 + d2);
        float iy = (mass / 12.0f) * (w2 + d2);
        float iz = (mass / 12.0f) * (w2 + h2);

        Matrix3f localInvInertia = new Matrix3f();
        localInvInertia.m00(1.0f / ix);
        localInvInertia.m11(1.0f / iy);
        localInvInertia.m22(1.0f / iz);

        Matrix3f rot = Matrix3f.fromQuaternion(this.rotation());
        Matrix3f rotT = new Matrix3f(rot).transpose();

        return new Matrix3f(rot).multiply(localInvInertia).multiply(rotT);
    }

    @Override
    public void updateAABBtoMatchPosition() {
        Matrix3f rot = Matrix3f.fromQuaternion(this.obb.rotation());
        Vector3f halfExtents = obb.halfExtents();

        float ex = Math.abs(rot.m00()) * halfExtents.x() + Math.abs(rot.m01()) * halfExtents.y() + Math.abs(rot.m02()) * halfExtents.z();
        float ey = Math.abs(rot.m10()) * halfExtents.x() + Math.abs(rot.m11()) * halfExtents.y() + Math.abs(rot.m12()) * halfExtents.z();
        float ez = Math.abs(rot.m20()) * halfExtents.x() + Math.abs(rot.m21()) * halfExtents.y() + Math.abs(rot.m22()) * halfExtents.z();

        aabb.min().setFrom(new Vector3f(obb.position().x() - ex, obb.position().y() - ey, obb.position().z() - ez));
        aabb.max().setFrom(new Vector3f(obb.position().x() + ex, obb.position().y() + ey, obb.position().z() + ez));
    }

    @Override
    public boolean resolveStatic(Arena arena, Collider other) {
        if (other instanceof OBBCollider o) {
            SATResult result = this.obb.intersects(o.obb);
            return result.collided();
        } else if (other instanceof AxisAlignedBoxCollider a) {
            SATResult result = this.obb.intersects(new OBB(a.aabb()));
            return result.collided();
        }
        return false;
    }

    @Override
    public boolean resolveDynamic(Arena arena, Collider other) {
        if (other instanceof OBBCollider o) {
            SATResult result = this.obb.intersects(o.obb);
            return result.collided();
        } else if (other instanceof AxisAlignedBoxCollider a) {
            SATResult result = this.obb.intersects(new OBB(a.aabb()));
            return result.collided();
        }
        return false;
    }

    @Override
    public AABB aabb() {
        return aabb;
    }
}
