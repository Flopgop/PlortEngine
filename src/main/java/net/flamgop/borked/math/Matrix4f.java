package net.flamgop.borked.math;

import org.jetbrains.annotations.ApiStatus;
import org.lwjgl.assimp.AIMatrix4x4;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.Buffer;

@SuppressWarnings({"UnusedReturnValue", "DuplicatedCode"})
public class Matrix4f {
    public static final int BYTES = 16 * Float.BYTES;
    private static final ValueLayout.OfFloat F32 = ValueLayout.JAVA_FLOAT;

    public static Matrix4f fromQuaternion(Arena arena, Quaternionf q) {
        return new Matrix4f(arena).rotation(q);
    }

    public static Matrix4f fromQuaternion(Quaternionf q) {
        return fromQuaternion(Arena.ofAuto(), q);
    }

    public static Matrix4f fromAssimp(AIMatrix4x4 m) {
        return new Matrix4f(
                m.a1(), m.b1(), m.c1(), m.d1(),
                m.a2(), m.b2(), m.c2(), m.d2(),
                m.a3(), m.b3(), m.c3(), m.d3(),
                m.a4(), m.b4(), m.c4(), m.d4()
        );
    }

    private static final long M00 = 0;
    private static final long M01 = 4;
    private static final long M02 = 8;
    private static final long M03 = 12;
    private static final long M10 = 16;
    private static final long M11 = 20;
    private static final long M12 = 24;
    private static final long M13 = 28;
    private static final long M20 = 32;
    private static final long M21 = 36;
    private static final long M22 = 40;
    private static final long M23 = 44;
    private static final long M30 = 48;
    private static final long M31 = 52;
    private static final long M32 = 56;
    private static final long M33 = 60;

    private final MemorySegment memory;

    public Matrix4f(Arena arena) {
        this.memory = arena.allocate(BYTES);
        this.setIdentity();
    }

    public Matrix4f() {
        this(Arena.ofAuto());
    }

    public Matrix4f(Arena arena, Matrix4f other) {
        this(arena);
        this.memory.copyFrom(other.memory);
    }

    public Matrix4f(Matrix4f other) {
        this(Arena.ofAuto(), other);
    }

    public Matrix4f(Arena arena, float m00, float m01, float m02, float m03, float m10, float m11, float m12, float m13, float m20, float m21, float m22, float m23, float m30, float m31, float m32, float m33) {
        this(arena);
        m00(m00); m01(m01); m02(m02); m03(m03);
        m10(m10); m11(m11); m12(m12); m13(m13);
        m20(m20); m21(m21); m22(m22); m23(m23);
        m30(m30); m31(m31); m32(m32); m33(m33);
    }

    public Matrix4f(float m00, float m01, float m02, float m03, float m10, float m11, float m12, float m13, float m20, float m21, float m22, float m23, float m30, float m31, float m32, float m33) {
        this(Arena.ofAuto(), m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23, m30, m31, m32, m33);
    }

    @ApiStatus.Internal // not bounds checked
    public float getUnsafe(int index) {
        return memory.get(F32, (long)index * Float.BYTES);
    }

    public float get(int index) {
        if (index < 0 || index >= 16) throw new IndexOutOfBoundsException(index);
        return getUnsafe(index);
    }

    @ApiStatus.Internal // not bounds checked
    public Matrix4f setUnsafe(int index, float value) {
        memory.set(F32, (long)index * Float.BYTES, value);
        return this;
    }

    public Matrix4f set(int index, float value) {
        if (index < 0 || index >= 16) throw new IndexOutOfBoundsException(index);
        return setUnsafe(index, value);
    }

    public Matrix4f setIdentity() {
        memory.fill((byte)0);
        memory.set(F32, M00, 1);
        memory.set(F32, M11, 1);
        memory.set(F32, M22, 1);
        memory.set(F32, M33, 1);
        return this;
    }

    public Vector3f position() {
        return new Vector3f(m30(), m31(), m32());
    }

    public Matrix4f perspective(float fov, float aspectRatio, float near, float far, boolean zZeroToOne) {
        float h = (float) Math.tan(fov * 0.5f);

        this.setIdentity();
        this.m00(1.0f / (h * aspectRatio));
        this.m11(1.0f / h);
        this.m22((zZeroToOne ? far : far + near) / (near - far));
        this.m32((zZeroToOne ? far : far + far) * near / (near - far));
        this.m23(-1);
        this.m33(0);

        return this;
    }

    public Matrix4f orthographic(float left, float right, float bottom, float top, float near, float far, boolean zZeroToOne) {
        this.setIdentity();
        this.m00(2f / (right - left));
        this.m11(2f / (top - bottom));
        this.m30(-(right + left) / (right - left));
        this.m31(-(top + bottom) / (top - bottom));

        if (zZeroToOne) {
            this.m22(-1f / (far - near));
            this.m32(-near / (far - near));
        } else {
            this.m22(-2f / (far - near));
            this.m32(-(far + near) / (far - near));
        }

        return this;
    }

    public Matrix4f lookAt(Vector3f position, Vector3f target, Vector3f up) {
        Vector3f dir = new Vector3f(position).subtract(target).normalize();
        Vector3f left = new Vector3f(up).cross(dir).normalize();
        Vector3f upn = new Vector3f(dir).cross(left).normalize();

        this.m00(left.x());
        this.m01(upn.x());
        this.m02(dir.x());
        this.m03(0.0f);
        this.m10(left.y());
        this.m11(upn.y());
        this.m12(dir.y());
        this.m13(0.0f);
        this.m20(left.z());
        this.m21(upn.z());
        this.m22(dir.z());
        this.m23(0.0f);
        this.m30(-left.dot(position));
        this.m31(-upn.dot(position));
        this.m32(-dir.dot(position));
        this.m33(1.0f);

        return this;
    }

    /// equivalent to this = this * right
    public Matrix4f multiply(Matrix4f right) {
        float a00 = this.m00(), a10 = this.m10(), a20 = this.m20(), a30 = this.m30();
        float a01 = this.m01(), a11 = this.m11(), a21 = this.m21(), a31 = this.m31();
        float a02 = this.m02(), a12 = this.m12(), a22 = this.m22(), a32 = this.m32();
        float a03 = this.m03(), a13 = this.m13(), a23 = this.m23(), a33 = this.m33();

        float b00 = right.m00(), b10 = right.m10(), b20 = right.m20(), b30 = right.m30();
        float b01 = right.m01(), b11 = right.m11(), b21 = right.m21(), b31 = right.m31();
        float b02 = right.m02(), b12 = right.m12(), b22 = right.m22(), b32 = right.m32();
        float b03 = right.m03(), b13 = right.m13(), b23 = right.m23(), b33 = right.m33();

        this.m00(Math.fma(a00, b00, Math.fma(a10, b01, Math.fma(a20, b02, a30 * b03))));
        this.m01(Math.fma(a01, b00, Math.fma(a11, b01, Math.fma(a21, b02, a31 * b03))));
        this.m02(Math.fma(a02, b00, Math.fma(a12, b01, Math.fma(a22, b02, a32 * b03))));
        this.m03(Math.fma(a03, b00, Math.fma(a13, b01, Math.fma(a23, b02, a33 * b03))));
        this.m10(Math.fma(a00, b10, Math.fma(a10, b11, Math.fma(a20, b12, a30 * b13))));
        this.m11(Math.fma(a01, b10, Math.fma(a11, b11, Math.fma(a21, b12, a31 * b13))));
        this.m12(Math.fma(a02, b10, Math.fma(a12, b11, Math.fma(a22, b12, a32 * b13))));
        this.m13(Math.fma(a03, b10, Math.fma(a13, b11, Math.fma(a23, b12, a33 * b13))));
        this.m20(Math.fma(a00, b20, Math.fma(a10, b21, Math.fma(a20, b22, a30 * b23))));
        this.m21(Math.fma(a01, b20, Math.fma(a11, b21, Math.fma(a21, b22, a31 * b23))));
        this.m22(Math.fma(a02, b20, Math.fma(a12, b21, Math.fma(a22, b22, a32 * b23))));
        this.m23(Math.fma(a03, b20, Math.fma(a13, b21, Math.fma(a23, b22, a33 * b23))));
        this.m30(Math.fma(a00, b30, Math.fma(a10, b31, Math.fma(a20, b32, a30 * b33))));
        this.m31(Math.fma(a01, b30, Math.fma(a11, b31, Math.fma(a21, b32, a31 * b33))));
        this.m32(Math.fma(a02, b30, Math.fma(a12, b31, Math.fma(a22, b32, a32 * b33))));
        this.m33(Math.fma(a03, b30, Math.fma(a13, b31, Math.fma(a23, b32, a33 * b33))));
        return this;
    }

    public Vector3f transform(Vector3f v) {
        float x = v.x(), y = v.y(), z = v.z();

        float rx = Math.fma(m00(), x, Math.fma(m10(), y, Math.fma(m20(), z, m30())));
        float ry = Math.fma(m01(), x, Math.fma(m11(), y, Math.fma(m21(), z, m31())));
        float rz = Math.fma(m02(), x, Math.fma(m12(), y, Math.fma(m22(), z, m32())));
        float rw = Math.fma(m03(), x, Math.fma(m13(), y, Math.fma(m23(), z, m33())));

        if (rw != 1.0f && rw != 0.0f) {
            float invW = 1.0f / rw;
            return new Vector3f(rx * invW, ry * invW, rz * invW);
        }

        return new Vector3f(rx, ry, rz);
    }

    public Vector3f transformDirection(Vector3f v) {
        float x = v.x(), y = v.y(), z = v.z();
        return new Vector3f(
                Math.fma(m00(), x, Math.fma(m10(), y, m20() * z)),
                Math.fma(m01(), x, Math.fma(m11(), y, m21() * z)),
                Math.fma(m02(), x, Math.fma(m12(), y, m22() * z))
        ).normalize();
    }

    public Matrix4f scale(float scale) {
        this.m00(this.m00() * scale);
        this.m01(this.m01() * scale);
        this.m02(this.m02() * scale);
        this.m03(this.m03() * scale);

        this.m10(this.m10() * scale);
        this.m11(this.m11() * scale);
        this.m12(this.m12() * scale);
        this.m13(this.m13() * scale);

        this.m20(this.m20() * scale);
        this.m21(this.m21() * scale);
        this.m22(this.m22() * scale);
        this.m23(this.m23() * scale);

        this.m30(this.m30() * scale);
        this.m31(this.m31() * scale);
        this.m32(this.m32() * scale);
        this.m33(this.m33() * scale);

        return this;
    }

    public float determinant() {
        float a00 = this.m00(), a10 = this.m10(), a20 = this.m20(), a30 = this.m30();
        float a01 = this.m01(), a11 = this.m11(), a21 = this.m21(), a31 = this.m31();
        float a02 = this.m02(), a12 = this.m12(), a22 = this.m22(), a32 = this.m32();
        float a03 = this.m03(), a13 = this.m13(), a23 = this.m23(), a33 = this.m33();

        return (a00*a11 - a01*a10) * (a22*a33 - a23*a32) - (a00*a12 - a02*a10) * (a21*a33 - a23*a31) + (a00*a13 - a03*a10) * (a21*a32 - a22*a31) + (a01*a12 - a02*a11) * (a20*a33 - a23*a30) - (a01*a13 - a03*a11) * (a20*a32 - a22*a30) + (a02*a13 - a03*a12) * (a20*a31 - a21*a30);
    }

    public Matrix4f adjugate() {
        float a00 = this.m00(), a10 = this.m10(), a20 = this.m20(), a30 = this.m30();
        float a01 = this.m01(), a11 = this.m11(), a21 = this.m21(), a31 = this.m31();
        float a02 = this.m02(), a12 = this.m12(), a22 = this.m22(), a32 = this.m32();
        float a03 = this.m03(), a13 = this.m13(), a23 = this.m23(), a33 = this.m33();

        float det00 = a22 * a33 - a32 * a23;
        float det01 = a21 * a33 - a31 * a23;
        float det02 = a21 * a32 - a31 * a22;
        float det03 = a20 * a33 - a30 * a23;
        float det04 = a20 * a32 - a30 * a22;
        float det05 = a20 * a31 - a30 * a21;
        float det06 = a12 * a33 - a32 * a13;
        float det07 = a11 * a33 - a31 * a13;
        float det08 = a11 * a32 - a31 * a12;
        float det09 = a10 * a33 - a30 * a13;
        float det10 = a10 * a32 - a30 * a12;
        float det11 = a10 * a31 - a30 * a11;
        float det12 = a12 * a23 - a22 * a13;
        float det13 = a11 * a23 - a21 * a13;
        float det14 = a11 * a22 - a21 * a12;
        float det15 = a10 * a23 - a20 * a13;
        float det16 = a10 * a22 - a20 * a12;
        float det17 = a10 * a21 - a20 * a11;

        this.m00( (a11*det00 - a12*det01 + a13*det02));
        this.m01(-(a01*det00 - a02*det01 + a03*det02));
        this.m02( (a01*det06 - a02*det07 + a03*det08));
        this.m03(-(a01*det12 - a02*det13 + a03*det14));

        this.m10(-(a10*det00 - a12*det03 + a13*det04));
        this.m11( (a00*det00 - a02*det03 + a03*det04));
        this.m12(-(a00*det06 - a02*det09 + a03*det10));
        this.m13( (a00*det12 - a02*det15 + a03*det16));

        this.m20( (a10*det01 - a11*det03 + a13*det05));
        this.m21(-(a00*det01 - a01*det03 + a03*det05));
        this.m22( (a00*det07 - a01*det09 + a03*det11));
        this.m23(-(a00*det13 - a01*det15 + a03*det17));

        this.m30(-(a10*det02 - a11*det04 + a12*det05));
        this.m31( (a00*det02 - a01*det04 + a02*det05));
        this.m32(-(a00*det08 - a01*det10 + a02*det11));
        this.m33( (a00*det14 - a01*det16 + a02*det17));
        return this;
    }

    public Matrix4f invert() {
        float det = determinant();
        if (det == 0f) throw new ArithmeticException("Cannot invert a matrix with determinant of 0!");
        float invDet = 1.0f / det;

        return adjugate().scale(invDet);
    }

    public Matrix4f translation(float x, float y, float z) {
        this.setIdentity();
        return setTranslation(x,y,z);
    }

    public Matrix4f setTranslation(float x, float y, float z) {
        this.m30(x);
        this.m31(y);
        this.m32(z);

        return this;
    }

    public Matrix4f translate(float x, float y, float z) {
        this.m30(m30() + x);
        this.m31(m31() + y);
        this.m32(m32() + z);
        return this;
    }

    public Matrix4f rotation(Quaternionf rotation) {
        this.setIdentity();
        return setRotation(rotation);
    }

    public Matrix4f setRotation(Quaternionf rotation) {
        float w = rotation.w(), x = rotation.x(), y = rotation.y(), z = rotation.z();
        float w2 = w * w;
        float x2 = x * x;
        float y2 = y * y;
        float z2 = z * z;
        float zw = z * w, dzw = zw + zw;
        float xy = x * y, dxy = xy + xy;
        float xz = x * z, dxz = xz + xz;
        float yw = y * w, dyw = yw + yw;
        float yz = y * z, dyz = yz + yz;
        float xw = x * w, dxw = xw + xw;

        this.m00(w2 + x2 - z2 - y2);
        this.m01(dxy + dzw);
        this.m02(dxz - dyw);
        this.m10(-dzw + dxy);
        this.m11(y2 - z2 + w2 - x2);
        this.m12(dyz + dxw);
        this.m20(dyw + dxz);
        this.m21(dyz - dxw);
        this.m22(z2 - y2 - x2 + w2);

        return this;
    }

    public Matrix4f rotate(Quaternionf rotation) {
        Matrix4f rotationMatrix = new Matrix4f().rotation(rotation);
        return this.multiply(rotationMatrix);
    }

    public Matrix4f transpose() {
        float a10 = this.m10(), a20 = this.m20(), a30 = this.m30();
        float a01 = this.m01(), a21 = this.m21(), a31 = this.m31();
        float a02 = this.m02(), a12 = this.m12(), a32 = this.m32();
        float a03 = this.m03(), a13 = this.m13(), a23 = this.m23();

        this.m01(a10);
        this.m02(a20);
        this.m03(a30);

        this.m10(a01);
        this.m12(a21);
        this.m13(a31);

        this.m20(a02);
        this.m21(a12);
        this.m23(a32);

        this.m30(a03);
        this.m31(a13);
        this.m32(a23);

        return this;
    }

    public float m00() {
        return this.memory.get(F32, M00);
    }
    public float m01() {
        return this.memory.get(F32, M01);
    }
    public float m02() {
        return this.memory.get(F32, M02);
    }
    public float m03() {
        return this.memory.get(F32, M03);
    }
    public float m10() {
        return this.memory.get(F32, M10);
    }
    public float m11() {
        return this.memory.get(F32, M11);
    }
    public float m12() {
        return this.memory.get(F32, M12);
    }
    public float m13() {
        return this.memory.get(F32, M13);
    }
    public float m20() {
        return this.memory.get(F32, M20);
    }
    public float m21() {
        return this.memory.get(F32, M21);
    }
    public float m22() {
        return this.memory.get(F32, M22);
    }
    public float m23() {
        return this.memory.get(F32, M23);
    }
    public float m30() {
        return this.memory.get(F32, M30);
    }
    public float m31() {
        return this.memory.get(F32, M31);
    }
    public float m32() {
        return this.memory.get(F32, M32);
    }
    public float m33() {
        return this.memory.get(F32, M33);
    }

    public void m00(float v) {
        this.memory.set(F32, M00, v);
    }
    public void m01(float v) {
        this.memory.set(F32, M01, v);
    }
    public void m02(float v) {
        this.memory.set(F32, M02, v);
    }
    public void m03(float v) {
        this.memory.set(F32, M03, v);
    }
    public void m10(float v) {
        this.memory.set(F32, M10, v);
    }
    public void m11(float v) {
        this.memory.set(F32, M11, v);
    }
    public void m12(float v) {
        this.memory.set(F32, M12, v);
    }
    public void m13(float v) {
        this.memory.set(F32, M13, v);
    }
    public void m20(float v) {
        this.memory.set(F32, M20, v);
    }
    public void m21(float v) {
        this.memory.set(F32, M21, v);
    }
    public void m22(float v) {
        this.memory.set(F32, M22, v);
    }
    public void m23(float v) {
        this.memory.set(F32, M23, v);
    }
    public void m30(float v) {
        this.memory.set(F32, M30, v);
    }
    public void m31(float v) {
        this.memory.set(F32, M31, v);
    }
    public void m32(float v) {
        this.memory.set(F32, M32, v);
    }
    public void m33(float v) {
        this.memory.set(F32, M33, v);
    }

    public void getToBuffer(Buffer buffer) {
        MemorySegment dst = MemorySegment.ofBuffer(buffer);
        getToMemorySegment(dst);
    }

    public void getToAddress(long ptr) {
        MemorySegment segment = MemorySegment.ofAddress(ptr).reinterpret(BYTES);
        getToMemorySegment(segment);
    }

    public void getToMemorySegment(MemorySegment dst) {
        dst.copyFrom(memory);
    }
}
