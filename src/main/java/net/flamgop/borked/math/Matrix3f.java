package net.flamgop.borked.math;

import org.jetbrains.annotations.ApiStatus;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class Matrix3f {
    public static final int BYTES = 9 * Float.BYTES;
    private static final ValueLayout.OfFloat F32 = ValueLayout.JAVA_FLOAT;

    public static Matrix3f fromQuaternion(Arena arena, Quaternionf q) {
        return new Matrix3f(arena).rotation(q);
    }

    public static Matrix3f fromQuaternion(Quaternionf q) {
        return fromQuaternion(Arena.ofAuto(), q);
    }

    private static final long M00 = 0;
    private static final long M01 = 4;
    private static final long M02 = 8;
    private static final long M10 = 12;
    private static final long M11 = 16;
    private static final long M12 = 20;
    private static final long M20 = 24;
    private static final long M21 = 28;
    private static final long M22 = 32;

    private final MemorySegment memory;

    public Matrix3f(Arena arena) {
        this.memory = arena.allocate(BYTES);
        this.identity();
    }

    public Matrix3f() {
        this(Arena.ofAuto());
    }

    public Matrix3f(Arena arena, Matrix3f other) {
        this(arena);
        this.memory.copyFrom(other.memory);
    }

    public Matrix3f(Matrix3f other) {
        this(Arena.ofAuto(), other);
    }

    public Matrix3f(Arena arena, float m00, float m01, float m02, float m10, float m11, float m12, float m20, float m21, float m22) {
        this(arena);
        m00(m00); m01(m01); m02(m02);
        m10(m10); m11(m11); m12(m12);
        m20(m20); m21(m21); m22(m22);
    }

    @ApiStatus.Internal
    public float getUnsafe(int index) {
        return memory.get(F32, (long)index * Float.BYTES);
    }

    public float get(int index) {
        if (index < 0 || index >= 9) throw new IndexOutOfBoundsException(index);
        return getUnsafe(index);
    }

    public Vector3f getColumn(int index) {
        return getColumn(Arena.ofAuto(), index);
    }

    public Vector3f getColumn(Arena arena, int index) {
        if (index < 0 || index >= 3) throw new IndexOutOfBoundsException(index);
        return new Vector3f(arena, getUnsafe(index * 3), getUnsafe(index * 3 + 1), getUnsafe(index * 3 + 2));
    }

    public Matrix3f identity() {
        memory.fill((byte)0);
        memory.set(F32, M00, 1.0f);
        memory.set(F32, M11, 1.0f);
        memory.set(F32, M22, 1.0f);
        return this;
    }

    public Matrix3f multiply(Matrix3f right) {
        float a00 = m00(), a10 = m10(), a20 = m20();
        float a01 = m01(), a11 = m11(), a21 = m21();
        float a02 = m02(), a12 = m12(), a22 = m22();

        float b00 = right.m00(), b10 = right.m10(), b20 = right.m20();
        float b01 = right.m01(), b11 = right.m11(), b21 = right.m21();
        float b02 = right.m02(), b12 = right.m12(), b22 = right.m22();

        this.m00(Math.fma(a00, b00, Math.fma(a10, b01, a20 * b02)));
        this.m01(Math.fma(a01, b00, Math.fma(a11, b01, a21 * b02)));
        this.m02(Math.fma(a02, b00, Math.fma(a12, b01, a22 * b02)));

        this.m10(Math.fma(a00, b10, Math.fma(a10, b11, a20 * b12)));
        this.m11(Math.fma(a01, b10, Math.fma(a11, b11, a21 * b12)));
        this.m12(Math.fma(a02, b10, Math.fma(a12, b11, a22 * b12)));

        this.m20(Math.fma(a00, b20, Math.fma(a10, b21, a20 * b22)));
        this.m21(Math.fma(a01, b20, Math.fma(a11, b21, a21 * b22)));
        this.m22(Math.fma(a02, b20, Math.fma(a12, b21, a22 * b22)));
        return this;
    }

    public Vector3f transform(Vector3f v) {
        return transform(Arena.ofAuto(), v);
    }

    public Vector3f transform(Arena arena, Vector3f v) {
        float x = v.x(), y = v.y(), z = v.z();
        return new Vector3f(
                arena,
                Math.fma(m00(), x, Math.fma(m10(), y, m20() * z)),
                Math.fma(m01(), x, Math.fma(m11(), y, m21() * z)),
                Math.fma(m02(), x, Math.fma(m12(), y, m22() * z))
        );
    }

    public float determinant() {
        return m00() * (m11() * m22() - m12() * m21())
                - m10() * (m01() * m22() - m02() * m21())
                + m20() * (m01() * m12() - m02() * m11());
    }

    public Matrix3f invert() {
        float a00 = m00(), a01 = m01(), a02 = m02();
        float a10 = m10(), a11 = m11(), a12 = m12();
        float a20 = m20(), a21 = m21(), a22 = m22();

        float det = a00 * (a11 * a22 - a12 * a21)
                - a10 * (a01 * a22 - a02 * a21)
                + a20 * (a01 * a12 - a02 * a11);

        if (det == 0f) throw new ArithmeticException("Matrix is singular");
        float invDet = 1.0f / det;

        this.m00((a11 * a22 - a21 * a12) * invDet);
        this.m01((a02 * a21 - a01 * a22) * invDet);
        this.m02((a01 * a12 - a02 * a11) * invDet);
        this.m10((a12 * a20 - a10 * a22) * invDet);
        this.m11((a00 * a22 - a02 * a20) * invDet);
        this.m12((a10 * a02 - a00 * a12) * invDet);
        this.m20((a10 * a21 - a11 * a20) * invDet);
        this.m21((a20 * a01 - a00 * a21) * invDet);
        this.m22((a00 * a11 - a10 * a01) * invDet);

        return this;
    }

    public Matrix3f rotation(Quaternionf rotation) {
        this.identity();
        return setRotation(rotation);
    }

    public Matrix3f setRotation(Quaternionf q) {
        float w = q.w(), x = q.x(), y = q.y(), z = q.z();
        float x2 = x + x, y2 = y + y, z2 = z + z;
        float xx = x * x2, xy = x * y2, xz = x * z2;
        float yy = y * y2, yz = y * z2, zz = z * z2;
        float wx = w * x2, wy = w * y2, wz = w * z2;

        this.m00(1.0f - (yy + zz));
        this.m01(xy + wz);
        this.m02(xz - wy);
        this.m10(xy - wz);
        this.m11(1.0f - (xx + zz));
        this.m12(yz + wx);
        this.m20(xz + wy);
        this.m21(yz - wx);
        this.m22(1.0f - (xx + yy));

        return this;
    }

    public Matrix3f transpose() {
        float a10 = m10(), a20 = m20(), a01 = m01(), a21 = m21(), a02 = m02(), a12 = m12();
        this.m01(a10); this.m02(a20);
        this.m10(a01); this.m12(a21);
        this.m20(a02); this.m21(a12);
        return this;
    }

    public float m00() { return memory.get(F32, M00); }
    public float m01() { return memory.get(F32, M01); }
    public float m02() { return memory.get(F32, M02); }
    public float m10() { return memory.get(F32, M10); }
    public float m11() { return memory.get(F32, M11); }
    public float m12() { return memory.get(F32, M12); }
    public float m20() { return memory.get(F32, M20); }
    public float m21() { return memory.get(F32, M21); }
    public float m22() { return memory.get(F32, M22); }

    public void m00(float v) { memory.set(F32, M00, v); }
    public void m01(float v) { memory.set(F32, M01, v); }
    public void m02(float v) { memory.set(F32, M02, v); }
    public void m10(float v) { memory.set(F32, M10, v); }
    public void m11(float v) { memory.set(F32, M11, v); }
    public void m12(float v) { memory.set(F32, M12, v); }
    public void m20(float v) { memory.set(F32, M20, v); }
    public void m21(float v) { memory.set(F32, M21, v); }
    public void m22(float v) { memory.set(F32, M22, v); }

    public void getToMemorySegment(MemorySegment dst) {
        dst.copyFrom(memory);
    }
}
