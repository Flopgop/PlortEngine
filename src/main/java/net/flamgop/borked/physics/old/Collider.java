package net.flamgop.borked.physics.old;

import net.flamgop.borked.math.Matrix3f;
import net.flamgop.borked.math.Quaternionf;
import net.flamgop.borked.math.Vector3f;

import java.lang.foreign.Arena;

public interface Collider extends AABBHolder {
    boolean dynamic();
    float mass();
    float restitution();
    float friction();

    Vector3f position(); // mutable
    Quaternionf rotation(); // mutable
    Vector3f linearVelocity(); // mutable
    Vector3f angularVelocity(); // mutable

    void updateAABBtoMatchPosition(); // AABB has to be in world space
    boolean resolveStatic(Arena arena, Collider other); // other is always static, returns true if a resolution happened
    boolean resolveDynamic(Arena arena, Collider other); // other is always dynamic, returns true if a resolution happened
    Matrix3f inverseInertiaTensorWorld();
}
