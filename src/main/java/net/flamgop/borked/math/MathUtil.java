package net.flamgop.borked.math;

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
}
