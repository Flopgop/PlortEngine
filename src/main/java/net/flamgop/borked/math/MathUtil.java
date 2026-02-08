package net.flamgop.borked.math;

import net.flamgop.borked.math.val.AABB;
import net.flamgop.borked.math.val.Matrix3f;
import net.flamgop.borked.math.val.Quaternionf;
import net.flamgop.borked.math.val.Vector3f;

public class MathUtil {
    /// Framerate-independent linear interpolation between two float values.
    /// See [Freya Holmér's video](https://www.youtube.com/watch?v=LSNQuFEDOyQ) about this algorithm.
    /// @param a Value to interpolate from
    /// @param b Value to interpolate to
    /// @param t Percentage between the two values to interpolate by (between 0 and 1)
    /// @param dt The time the last frame took, in seconds.
    /// @return The linearly interpolated value.
    public static float lerpf(float a, float b, float t, float dt) {
        return (a - b) * (float)Math.pow(t, dt) + b;
    }

    /// Framerate-independent linear interpolation between two double values.
    /// See [Freya Holmér's video](https://www.youtube.com/watch?v=LSNQuFEDOyQ) about this algorithm.
    /// @param a Value to interpolate from
    /// @param b Value to interpolate to
    /// @param t Percentage between the two values to interpolate by (between 0 and 1)
    /// @param dt The time the last frame took, in seconds.
    /// @return The linearly interpolated value.
    public static double lerpd(double a, double b, double t, double dt) {
        return (a - b) * Math.pow(t, dt) + b;
    }

    /// Framerate-independent linear interpolation between two angular float values (in degrees).
    /// See [Freya Holmér's video](https://www.youtube.com/watch?v=LSNQuFEDOyQ) about this algorithm.
    /// @param a Value to interpolate from
    /// @param b Value to interpolate to
    /// @param t Percentage between the two values to interpolate by (between 0 and 1)
    /// @param dt The time the last frame took, in seconds.
    /// @return The linearly interpolated value.
    public static float lerpfAngle(float a, float b, float t, float dt) {
        double diff = Math.toRadians(b-a);
        float shortestDistance = (float) Math.atan2(Math.sin(diff), Math.cos(diff));
        float deltaDeg = (float) Math.toDegrees(shortestDistance);
        return a + deltaDeg * (1.0f - (float)Math.pow(t, dt));
    }

    /// Framerate-independent linear interpolation between two angular double values (in degrees).
    /// See [Freya Holmér's video](https://www.youtube.com/watch?v=LSNQuFEDOyQ) about this algorithm.
    /// @param a Value to interpolate from
    /// @param b Value to interpolate to
    /// @param t Percentage between the two values to interpolate by (between 0 and 1)
    /// @param dt The time the last frame took, in seconds.
    /// @return The linearly interpolated value.
    public static double lerpdAngle(double a, double b, double t, double dt) {
        double diff = Math.toRadians(b-a);
        double shortestDistance = Math.atan2(Math.sin(diff), Math.cos(diff));
        double deltaDeg = Math.toDegrees(shortestDistance);
        return a + deltaDeg * (1.0f - Math.pow(t, dt));
    }

    public static AABB computeOBBAABB(Vector3f position, Quaternionf rotation, Vector3f halfExtents) {
        Matrix3f rot = new Matrix3f().rotation(rotation);
        float worldHalfX = Math.abs(rot.m00() * halfExtents.x()) +
                Math.abs(rot.m10() * halfExtents.y()) +
                Math.abs(rot.m20() * halfExtents.z());

        float worldHalfY = Math.abs(rot.m01() * halfExtents.x()) +
                Math.abs(rot.m11() * halfExtents.y()) +
                Math.abs(rot.m21() * halfExtents.z());

        float worldHalfZ = Math.abs(rot.m02() * halfExtents.x()) +
                Math.abs(rot.m12() * halfExtents.y()) +
                Math.abs(rot.m22() * halfExtents.z());

        Vector3f min = new Vector3f(position.x() - worldHalfX, position.y() - worldHalfY, position.z() - worldHalfZ);
        Vector3f max = new Vector3f(position.x() + worldHalfX, position.y() + worldHalfY, position.z() + worldHalfZ);

        return new AABB(min, max);
    }
}
