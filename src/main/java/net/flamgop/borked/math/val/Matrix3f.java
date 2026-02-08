package net.flamgop.borked.math.val;

import org.jetbrains.annotations.Contract;

public value record Matrix3f(
        float m00, float m01, float m02,
        float m10, float m11, float m12,
        float m20, float m21, float m22
) {
    public static final int BYTES = 9 * Float.BYTES;

    public Matrix3f() {
        this(
                1,0,0,
                0,1,0,
                0,0,1
        );
    }

    @Contract(pure = true)
    public Matrix3f multiply(Matrix3f right) {
        return new Matrix3f(
                Math.fma(m00, right.m00, Math.fma(m10, right.m01, m20 * right.m02)),
                Math.fma(m01, right.m00, Math.fma(m11, right.m01, m21 * right.m02)),
                Math.fma(m02, right.m00, Math.fma(m12, right.m01, m22 * right.m02)),
                Math.fma(m00, right.m10, Math.fma(m10, right.m11, m20 * right.m12)),
                Math.fma(m01, right.m10, Math.fma(m11, right.m11, m21 * right.m12)),
                Math.fma(m02, right.m10, Math.fma(m12, right.m11, m22 * right.m12)),
                Math.fma(m00, right.m20, Math.fma(m10, right.m21, m20 * right.m22)),
                Math.fma(m01, right.m20, Math.fma(m11, right.m21, m21 * right.m22)),
                Math.fma(m02, right.m20, Math.fma(m12, right.m21, m22 * right.m22))
        );
    }

    @Contract(pure = true)
    public Vector3f transform(Vector3f v) {
        float x = v.x(), y = v.y(), z = v.z();
        return new Vector3f(
                Math.fma(m00, x, Math.fma(m10, y, m20 * z)),
                Math.fma(m01, x, Math.fma(m11, y, m21 * z)),
                Math.fma(m02, x, Math.fma(m12, y, m22 * z))
        );
    }

    public float determinant() {
        return m00 * (m11 * m22 - m12 * m21)
                - m10 * (m01 * m22 - m02 * m21)
                + m20 * (m01 * m12 - m02 * m11);
    }

    @Contract(pure = true)
    public Matrix3f invert() {
        float det = determinant();

        if (det == 0f) throw new ArithmeticException("Matrix is singular");
        float invDet = 1.0f / det;

        return new Matrix3f(
            (m11 * m22 - m21 * m12) * invDet,
            (m02 * m21 - m01 * m22) * invDet,
            (m01 * m12 - m02 * m11) * invDet,
            (m12 * m20 - m10 * m22) * invDet,
            (m00 * m22 - m02 * m20) * invDet,
            (m10 * m02 - m00 * m12) * invDet,
            (m10 * m21 - m11 * m20) * invDet,
            (m20 * m01 - m00 * m21) * invDet,
            (m00 * m11 - m10 * m01) * invDet
        );
    }

    @Contract(pure = true)
    public Matrix3f rotation(Quaternionf q) {
        float w = q.w(), x = q.x(), y = q.y(), z = q.z();
        float x2 = x + x, y2 = y + y, z2 = z + z;
        float xx = x * x2, xy = x * y2, xz = x * z2;
        float yy = y * y2, yz = y * z2, zz = z * z2;
        float wx = w * x2, wy = w * y2, wz = w * z2;

        return new Matrix3f(
            1.0f - (yy + zz),
            xy + wz,
            xz - wy,
            xy - wz,
            1.0f - (xx + zz),
            yz + wx,
            xz + wy,
            yz - wx,
            1.0f - (xx + yy)
        );
    }

    @Contract(pure = true)
    public Matrix3f transpose() {
        return new Matrix3f(
                m00, m10, m20,
                m01, m11, m21,
                m02, m12, m22
        );
    }
}
