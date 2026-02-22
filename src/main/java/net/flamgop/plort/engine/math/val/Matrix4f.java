package net.flamgop.plort.engine.math.val;

import org.jetbrains.annotations.Contract;
import org.lwjgl.assimp.AIMatrix4x4;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;


public value record Matrix4f(
        float m00, float m01, float m02, float m03,
        float m10, float m11, float m12, float m13,
        float m20, float m21, float m22, float m23,
        float m30, float m31, float m32, float m33
) {
    public static final int BYTES = 16 * Float.BYTES;

    @Contract(pure = true, value = "_, _, _, _, _ -> new")
    public static Matrix4f perspective(float fov, float aspectRatio, float near, float far, boolean zZeroToOne) {
        float h = (float) Math.tan(fov * 0.5f);
        return new Matrix4f(
                1.0f / (h * aspectRatio), 0, 0, 0,
                0, 1.0f / h, 0, 0,
                0, 0, (zZeroToOne ? far : far + near) / (near - far), -1,
                0, 0, (zZeroToOne ? far : far + far) * near / (near - far), 0
        );
    }

    @Contract(pure = true, value = "_, _, _, _, _, _, _ -> new")
    public static Matrix4f orthographic(float left, float right, float bottom, float top, float near, float far, boolean zZeroToOne) {
        return new Matrix4f(
                2f / (right - left), 0, 0, 0,
                0, 2f / (top - bottom), 0, 0,
                0, 0, zZeroToOne ? -1f / (far - near) : -2f / (far - near), 0,
                -(right + left) / (right - left),
                -(top + bottom) / (top - bottom),
                zZeroToOne ? -near / (far - near) : -(far + near) / (far - near),
                1f                                                                
        );
    }

    @Contract(pure = true, value = "_, _, _ -> new")
    public static Matrix4f lookAt(Vector3f position, Vector3f target, Vector3f up) {
        Vector3f dir = position.subtract(target).normalize();
        Vector3f tmpUp;
        if (Math.abs(dir.dot(up)) > 0.999f) {
            tmpUp = new Vector3f(1,0,0);
        } else {
            tmpUp = up;
        }

        Vector3f left = tmpUp.cross(dir).normalize();
        Vector3f upn = dir.cross(left).normalize();

        return new Matrix4f(
                left.x(), upn.x(), dir.x(), 0f,
                left.y(), upn.y(), dir.y(), 0f,
                left.z(), upn.z(), dir.z(), 0f,
                -left.dot(position), -upn.dot(position), -dir.dot(position), 1.0f
        );
    }

    @Contract(pure = true, value = "_ -> new")
    public static Matrix4f fromAssimp(AIMatrix4x4 m) {
        return new Matrix4f(
                m.a1(), m.b1(), m.c1(), m.d1(),
                m.a2(), m.b2(), m.c2(), m.d2(),
                m.a3(), m.b3(), m.c3(), m.d3(),
                m.a4(), m.b4(), m.c4(), m.d4()
        );
    }

    @Contract(pure = true)
    public Matrix4f() {
        this(
                1,0,0,0,
                0,1,0,0,
                0,0,1,0,
                0,0,0,1
        );
    }

    @Contract(pure = true, value = "-> new")
    public Vector3f position() {
        return new Vector3f(m30, m31, m32);
    }
    
    @Contract(pure = true, value = "_ -> new")
    public Matrix4f multiply(Matrix4f right) {
        return new Matrix4f(
                Math.fma(m00, right.m00, Math.fma(m10, right.m01, Math.fma(m20, right.m02, m30 * right.m03))),
                Math.fma(m01, right.m00, Math.fma(m11, right.m01, Math.fma(m21, right.m02, m31 * right.m03))),
                Math.fma(m02, right.m00, Math.fma(m12, right.m01, Math.fma(m22, right.m02, m32 * right.m03))),
                Math.fma(m03, right.m00, Math.fma(m13, right.m01, Math.fma(m23, right.m02, m33 * right.m03))),
                Math.fma(m00, right.m10, Math.fma(m10, right.m11, Math.fma(m20, right.m12, m30 * right.m13))),
                Math.fma(m01, right.m10, Math.fma(m11, right.m11, Math.fma(m21, right.m12, m31 * right.m13))),
                Math.fma(m02, right.m10, Math.fma(m12, right.m11, Math.fma(m22, right.m12, m32 * right.m13))),
                Math.fma(m03, right.m10, Math.fma(m13, right.m11, Math.fma(m23, right.m12, m33 * right.m13))),
                Math.fma(m00, right.m20, Math.fma(m10, right.m21, Math.fma(m20, right.m22, m30 * right.m23))),
                Math.fma(m01, right.m20, Math.fma(m11, right.m21, Math.fma(m21, right.m22, m31 * right.m23))),
                Math.fma(m02, right.m20, Math.fma(m12, right.m21, Math.fma(m22, right.m22, m32 * right.m23))),
                Math.fma(m03, right.m20, Math.fma(m13, right.m21, Math.fma(m23, right.m22, m33 * right.m23))),
                Math.fma(m00, right.m30, Math.fma(m10, right.m31, Math.fma(m20, right.m32, m30 * right.m33))),
                Math.fma(m01, right.m30, Math.fma(m11, right.m31, Math.fma(m21, right.m32, m31 * right.m33))),
                Math.fma(m02, right.m30, Math.fma(m12, right.m31, Math.fma(m22, right.m32, m32 * right.m33))),
                Math.fma(m03, right.m30, Math.fma(m13, right.m31, Math.fma(m23, right.m32, m33 * right.m33)))
        );
    }

    @Contract(pure = true, value = "_ -> new")
    public Vector4f transform(Vector4f v) {
        float x = v.x(), y = v.y(), z = v.z(), w = v.w();
        return new Vector4f(
                Math.fma(m00, x, Math.fma(m10, y, Math.fma(m20, z, m30 * w))),
                Math.fma(m01, x, Math.fma(m11, y, Math.fma(m21, z, m31 * w))),
                Math.fma(m02, x, Math.fma(m12, y, Math.fma(m22, z, m32 * w))),
                Math.fma(m03, x, Math.fma(m13, y, Math.fma(m23, z, m33 * w)))
        );
    }

    @Contract(pure = true, value = "_ -> new")
    public Vector3f transform(Vector3f v) {
        float x = v.x(), y = v.y(), z = v.z();

        float rx = Math.fma(m00, x, Math.fma(m10, y, Math.fma(m20, z, m30)));
        float ry = Math.fma(m01, x, Math.fma(m11, y, Math.fma(m21, z, m31)));
        float rz = Math.fma(m02, x, Math.fma(m12, y, Math.fma(m22, z, m32)));
        float rw = Math.fma(m03, x, Math.fma(m13, y, Math.fma(m23, z, m33)));

        if (rw != 1.0f && rw != 0.0f) {
            float invW = 1.0f / rw;
            return new Vector3f(rx * invW, ry * invW, rz * invW);
        }

        return new Vector3f(
                rx, ry, rz
        );
    }

    @Contract(pure = true, value = "_ -> new")
    public Vector3f transformDirection(Vector3f v) {
        float x = v.x(), y = v.y(), z = v.z();
        return new Vector3f(
                Math.fma(m00, x, Math.fma(m10, y, m20 * z)),
                Math.fma(m01, x, Math.fma(m11, y, m21 * z)),
                Math.fma(m02, x, Math.fma(m12, y, m22 * z))
        );
    }

    @Contract(pure = true, value = "_ -> new")
    public Matrix4f scale(float scale) {
        return new Matrix4f(
                m00 * scale, m01 * scale, m02 * scale, m03 * scale,
                m10 * scale, m11 * scale, m12 * scale, m13 * scale,
                m20 * scale, m21 * scale, m22 * scale, m23 * scale,
                m30 * scale, m31 * scale, m32 * scale, m33 * scale
        );
    }

    @Contract(pure = true, value = "_ -> new")
    public Matrix4f scale3x3(float scale) {
        return new Matrix4f(
                m00 * scale, m01 * scale, m02 * scale, m03,
                m10 * scale, m11 * scale, m12 * scale, m13,
                m20 * scale, m21 * scale, m22 * scale, m23,
                m30, m31, m32, m33
        );
    }

    @Contract(pure = true, value = "-> _")
    public float determinant() {
        return (m00*m11 - m01*m10) * (m22*m33 - m23*m32) - (m00*m12 - m02*m10) * (m21*m33 - m23*m31) + (m00*m13 - m03*m10) * (m21*m32 - m22*m31) + (m01*m12 - m02*m11) * (m20*m33 - m23*m30) - (m01*m13 - m03*m11) * (m20*m32 - m22*m30) + (m02*m13 - m03*m12) * (m20*m31 - m21*m30);
    }

    @Contract(pure = true, value = "-> new")
    public Matrix4f adjugate() {
        float det00 = m22 * m33 - m32 * m23;
        float det01 = m21 * m33 - m31 * m23;
        float det02 = m21 * m32 - m31 * m22;
        float det03 = m20 * m33 - m30 * m23;
        float det04 = m20 * m32 - m30 * m22;
        float det05 = m20 * m31 - m30 * m21;
        float det06 = m12 * m33 - m32 * m13;
        float det07 = m11 * m33 - m31 * m13;
        float det08 = m11 * m32 - m31 * m12;
        float det09 = m10 * m33 - m30 * m13;
        float det10 = m10 * m32 - m30 * m12;
        float det11 = m10 * m31 - m30 * m11;
        float det12 = m12 * m23 - m22 * m13;
        float det13 = m11 * m23 - m21 * m13;
        float det14 = m11 * m22 - m21 * m12;
        float det15 = m10 * m23 - m20 * m13;
        float det16 = m10 * m22 - m20 * m12;
        float det17 = m10 * m21 - m20 * m11;

        return new Matrix4f(
             (m11*det00 - m12*det01 + m13*det02),
            -(m01*det00 - m02*det01 + m03*det02),
             (m01*det06 - m02*det07 + m03*det08),
            -(m01*det12 - m02*det13 + m03*det14),
            -(m10*det00 - m12*det03 + m13*det04),
             (m00*det00 - m02*det03 + m03*det04),
            -(m00*det06 - m02*det09 + m03*det10),
             (m00*det12 - m02*det15 + m03*det16),
             (m10*det01 - m11*det03 + m13*det05),
            -(m00*det01 - m01*det03 + m03*det05),
             (m00*det07 - m01*det09 + m03*det11),
            -(m00*det13 - m01*det15 + m03*det17),
            -(m10*det02 - m11*det04 + m12*det05),
             (m00*det02 - m01*det04 + m02*det05),
            -(m00*det08 - m01*det10 + m02*det11),
             (m00*det14 - m01*det16 + m02*det17)
        );
    }

    @Contract(pure = true, value = "-> new")
    public Matrix4f invert() {
        float s0 = m00, s1 = m01, s2 = m02, s3 = m03;
        float s4 = m10, s5 = m11, s6 = m12, s7 = m13;
        float s8 = m20, s9 = m21, s10 = m22, s11 = m23;
        float s12 = m30, s13 = m31, s14 = m32, s15 = m33;

        float b00 = s8 * s13 - s9 * s12;
        float b01 = s8 * s14 - s10 * s12;
        float b02 = s8 * s15 - s11 * s12;
        float b03 = s9 * s14 - s10 * s13;
        float b04 = s9 * s15 - s11 * s13;
        float b05 = s10 * s15 - s11 * s14;

        float a00 = s0 * s5 - s1 * s4;
        float a01 = s0 * s6 - s2 * s4;
        float a02 = s0 * s7 - s3 * s4;
        float a03 = s1 * s6 - s2 * s5;
        float a04 = s1 * s7 - s3 * s5;
        float a05 = s2 * s7 - s3 * s6;

        float det = a00 * b05 - a01 * b04 + a02 * b03 + a03 * b02 - a04 * b01 + a05 * b00;

        if (Math.abs(det) < 1e-9f) throw new ArithmeticException("Matrix is singular");

        float invDet = 1.0f / det;

        return new Matrix4f(
            Math.fma(s5, b05, Math.fma(-s6, b04, s7 * b03)) * invDet,
            Math.fma(-s1, b05, Math.fma(s2, b04, -s3 * b03)) * invDet,
            Math.fma(s13, a05, Math.fma(-s14, a04, s15 * a03)) * invDet,
            Math.fma(-s9, a05, Math.fma(s10, a04, -s11 * a03)) * invDet,
            Math.fma(-s4, b05, Math.fma(s6, b02, -s7 * b01)) * invDet,
            Math.fma(s0, b05, Math.fma(-s2, b02, s3 * b01)) * invDet,
            Math.fma(-s12, a05, Math.fma(s14, a02, -s15 * a01)) * invDet,
            Math.fma(s8, a05, Math.fma(-s10, a02, s11 * a01)) * invDet,
            Math.fma(s4, b04, Math.fma(-s5, b02, s7 * b00)) * invDet,
            Math.fma(-s0, b04, Math.fma(s1, b02, -s3 * b00)) * invDet,
            Math.fma(s12, a04, Math.fma(-s13, a02, s15 * a00)) * invDet,
            Math.fma(-s8, a04, Math.fma(s9, a02, -s11 * a00)) * invDet,
            Math.fma(-s4, b03, Math.fma(s5, b01, -s6 * b00)) * invDet,
            Math.fma(s0, b03, Math.fma(-s1, b01, s2 * b00)) * invDet,
            Math.fma(-s12, a03, Math.fma(s13, a01, -s14 * a00)) * invDet,
            Math.fma(s8, a03, Math.fma(-s9, a01, s10 * a00)) * invDet
        );
    }

    @Contract(pure = true, value = "_, _, _ -> new")
    public Matrix4f translation(float x, float y, float z) {
        return new Matrix4f(
                m00, m01, m02, m03,
                m10, m11, m12, m13,
                m20, m21, m22, m23,
                x, y, z, m33
        );
    }

    @Contract(pure = true, value = "_, _, _ -> new")
    public Matrix4f translate(float x, float y, float z) {
        return new Matrix4f(
                m00, m01, m02, m03,
                m10, m11, m12, m13,
                m20, m21, m22, m23,
                m30 + x, m31 + y, m32 + z, m33
        );
    }

    @Contract(pure = true, value = "_ -> new")
    public Matrix4f translate(Vector3f v) {
        return translate(v.x(), v.y(), v.z());
    }

    @Contract(pure = true, value = "-> new")
    public Quaternionf rotation() {
        float trace = m00 + m11 + m22;
        float x, y, z, w;

        if (trace > 0) {
            float s = (float) Math.sqrt(trace + 1.0f) * 2f;
            w = 0.25f * s;
            x = (m12 - m21) / s;
            y = (m20 - m02) / s;
            z = (m01 - m10) / s;
        } else if ((m00 > m11) && (m00 > m22)) {
            float s = (float) Math.sqrt(1.0f + m00 - m11 - m22) * 2f;
            w = (m12 - m21) / s;
            x = 0.25f * s;
            y = (m01 + m10) / s;
            z = (m02 + m20) / s;
        } else if (m11 > m22) {
            float s = (float) Math.sqrt(1.0f + m11 - m00 - m22) * 2f;
            w = (m20 - m02) / s;
            x = (m01 + m10) / s;
            y = 0.25f * s;
            z = (m12 + m21) / s;
        } else {
            float s = (float) Math.sqrt(1.0f + m22 - m00 - m11) * 2f;
            w = (m01 - m10) / s;
            x = (m02 + m20) / s;
            y = (m12 + m21) / s;
            z = 0.25f * s;
        }

        return new Quaternionf(x, y, z, w);
    }

    @Contract(pure = true, value = "_ -> new")
    public Matrix4f rotation(Quaternionf rotation) {
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

        return new Matrix4f(
                w2 + x2 - z2 - y2, dxy + dzw, dxz - dyw, m03,
                -dzw + dxy, y2 - z2 + w2 - x2, dyz + dxw, m13,
                dyw + dxz, dyz - dxw, z2 - y2 - x2 + w2, m23,
                m30, m31, m32, m33
        );
    }

    @Contract(pure = true, value = "_ -> new")
    public Matrix4f rotate(Quaternionf rotation) {
        Matrix4f rotationMatrix = new Matrix4f().rotation(rotation);
        return this.multiply(rotationMatrix);
    }

    @Contract(pure = true, value = "-> new")
    public Matrix4f transpose() {
        return new Matrix4f(
                m00, m10, m20, m30,
                m01, m11, m21, m31,
                m02, m12, m22, m32,
                m03, m13, m23, m33
        );
    }

    @Contract(mutates = "param2")
    public void getToBuffer(int index, ByteBuffer buffer) {
        buffer.putFloat(index, m00);
        buffer.putFloat(index + Float.BYTES, m01);
        buffer.putFloat(index + 2 * Float.BYTES, m02);
        buffer.putFloat(index + 3 * Float.BYTES, m03);

        buffer.putFloat(index + 4 * Float.BYTES, m10);
        buffer.putFloat(index + 5 * Float.BYTES, m11);
        buffer.putFloat(index + 6 * Float.BYTES, m12);
        buffer.putFloat(index + 7 * Float.BYTES, m13);

        buffer.putFloat(index + 8 * Float.BYTES, m20);
        buffer.putFloat(index + 9 * Float.BYTES, m21);
        buffer.putFloat(index + 10 * Float.BYTES, m22);
        buffer.putFloat(index + 11 * Float.BYTES, m23);

        buffer.putFloat(index + 12 * Float.BYTES, m30);
        buffer.putFloat(index + 13 * Float.BYTES, m31);
        buffer.putFloat(index + 14 * Float.BYTES, m32);
        buffer.putFloat(index + 15 * Float.BYTES, m33);
    }

    @Contract(mutates = "io")
    public void getToAddress(long ptr) {
        MemorySegment segment = MemorySegment.ofAddress(ptr).reinterpret(BYTES);
        segment.set(ValueLayout.JAVA_FLOAT, 0, m00);
        segment.set(ValueLayout.JAVA_FLOAT, Float.BYTES, m01);
        segment.set(ValueLayout.JAVA_FLOAT, 2 * Float.BYTES, m02);
        segment.set(ValueLayout.JAVA_FLOAT, 3 * Float.BYTES, m03);

        segment.set(ValueLayout.JAVA_FLOAT, 4 * Float.BYTES, m10);
        segment.set(ValueLayout.JAVA_FLOAT, 5 * Float.BYTES, m11);
        segment.set(ValueLayout.JAVA_FLOAT, 6 * Float.BYTES, m12);
        segment.set(ValueLayout.JAVA_FLOAT, 7 * Float.BYTES, m13);

        segment.set(ValueLayout.JAVA_FLOAT, 8 * Float.BYTES, m20);
        segment.set(ValueLayout.JAVA_FLOAT, 9 * Float.BYTES, m21);
        segment.set(ValueLayout.JAVA_FLOAT, 10 * Float.BYTES, m22);
        segment.set(ValueLayout.JAVA_FLOAT, 11 * Float.BYTES, m23);

        segment.set(ValueLayout.JAVA_FLOAT, 12 * Float.BYTES, m30);
        segment.set(ValueLayout.JAVA_FLOAT, 13 * Float.BYTES, m31);
        segment.set(ValueLayout.JAVA_FLOAT, 14 * Float.BYTES, m32);
        segment.set(ValueLayout.JAVA_FLOAT, 15 * Float.BYTES, m33);
    }
}
