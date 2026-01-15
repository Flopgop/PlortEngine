package net.flamgop.borked.physics.old;

import net.flamgop.borked.math.AABB;
import net.flamgop.borked.math.Matrix3f;
import net.flamgop.borked.math.Quaternionf;
import net.flamgop.borked.math.Vector3f;

import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.List;

public class OBB {
    private final Vector3f halfExtents = new Vector3f();
    private final Vector3f position = new Vector3f();
    private final Quaternionf rotation = new Quaternionf();

    public OBB(Vector3f halfExtents) {
        this.halfExtents.setFrom(halfExtents);
    }

    public OBB(AABB aabb) {
        this.halfExtents.setFrom(new Vector3f(
                (aabb.max().x() - aabb.min().x()) * 0.5f,
                (aabb.max().y() - aabb.min().y()) * 0.5f,
                (aabb.max().z() - aabb.min().z()) * 0.5f
        ));

        this.position.setFrom(new Vector3f(
                (aabb.max().x() + aabb.min().x()) * 0.5f,
                (aabb.max().y() + aabb.min().y()) * 0.5f,
                (aabb.max().z() + aabb.min().z()) * 0.5f
        ));
    }

    public Vector3f position() {
        return position;
    }

    public Vector3f halfExtents() {
        return new Vector3f(halfExtents);
    }

    public Quaternionf rotation() {
        return rotation;
    }

    public Vector3f[] calculateSATAxes(Arena arena, Vector3f[] basisA, Vector3f[] basisB) {
        Vector3f[] axes = new Vector3f[15];

        for (int i = 0; i < 3; i++) axes[i] = new Vector3f(arena, basisA[i]);
        for (int i = 0; i < 3; i++) axes[3 + i] = new Vector3f(arena, basisB[i]);

        int index = 6;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                Vector3f cross = new Vector3f(arena, basisA[i]).cross(basisB[j]);
                if (cross.lengthSquared() < 1e-8f) {
                    axes[index++] = null;
                } else {
                    axes[index++] = cross;
                }
            }
        }
        return axes;
    }

    private Vector3f[] getBasisVectors(Arena arena, Matrix3f rot) {
        return new Vector3f[] {
                new Vector3f(arena, rot.m00(), rot.m10(), rot.m20()),
                new Vector3f(arena, rot.m01(), rot.m11(), rot.m21()),
                new Vector3f(arena, rot.m02(), rot.m12(), rot.m22())
        };
    }

    private float[] projectOntoAxis(Vector3f axis, Vector3f[] basis) {
        if (axis == null) return new float[] {0, 0};

        float r = 0;
        for (int i = 0; i < 3; i++) {
            float extent = (i == 0) ? halfExtents.x() : (i == 1) ? halfExtents.y() : halfExtents.z();
            r += Math.abs(axis.dot(basis[i])) * extent;
        }
        float c = axis.dot(position);
        return new float[] {c - r, c + r};
    }

    private List<Vector3f> getFaceVertices(Arena arena, Vector3f normal) {
        Matrix3f rot = Matrix3f.fromQuaternion(arena, this.rotation);
        Vector3f[] basis = getBasisVectors(arena, rot);

        int bestAxis = 0;
        float maxDot = -1.0f;
        for (int i = 0; i < 3; i++) {
            float dot = basis[i].dot(normal);
            if (Math.abs(dot) > maxDot) {
                maxDot = Math.abs(dot);
                bestAxis = i;
            }
        }

        Vector3f n = basis[bestAxis];
        if (n.dot(normal) < 0) n.scale(-1);

        Vector3f axis1 = basis[(bestAxis + 1) % 3];
        Vector3f axis2 = basis[(bestAxis + 2) % 3];
        float e1 = getExtent(bestAxis + 1);
        float e2 = getExtent(bestAxis + 2);
        float eN = getExtent(bestAxis);

        Vector3f center = new Vector3f(arena, n).scale(eN).add(position);
        List<Vector3f> vertices = new ArrayList<>();
        vertices.add(new Vector3f(arena, center).add(new Vector3f(arena, axis1).scale(e1)).add(new Vector3f(arena, axis2).scale(e2)));
        vertices.add(new Vector3f(arena, center).subtract(new Vector3f(arena, axis1).scale(e1)).add(new Vector3f(arena, axis2).scale(e2)));
        vertices.add(new Vector3f(arena, center).subtract(new Vector3f(arena, axis1).scale(e1)).subtract(new Vector3f(arena, axis2).scale(e2)));
        vertices.add(new Vector3f(arena, center).add(new Vector3f(arena, axis1).scale(e1)).subtract(new Vector3f(arena, axis2).scale(e2)));
        return vertices;
    }

    private float getExtent(int index) {
        index %= 3;
        return (index == 0) ? halfExtents.x() : (index == 1) ? halfExtents.y() : halfExtents.z();
    }

    public SATResult intersects(OBB other) {
        try (Arena arena = Arena.ofConfined()) {
            float minPenetration = Float.MAX_VALUE;
            Vector3f smallestAxis = null;

            Matrix3f rotA = Matrix3f.fromQuaternion(arena, this.rotation);
            Matrix3f rotB = Matrix3f.fromQuaternion(arena, other.rotation);

            Vector3f[] basisA = getBasisVectors(arena, rotA);
            Vector3f[] basisB = getBasisVectors(arena, rotB);

            Vector3f[] axes = calculateSATAxes(arena, basisA, basisB);

            for (Vector3f axis : axes) {
                if (axis == null) continue;
                axis.normalize();

                float[] projA = this.projectOntoAxis(axis, basisA);
                float[] projB = other.projectOntoAxis(axis, basisB);

                float overlap = Math.min(projA[1], projB[1]) - Math.max(projA[0], projB[0]);
                if (overlap < 0) return new SATResult(false, null, -1f, null);

                if (overlap < minPenetration) {
                    minPenetration = overlap;
                    smallestAxis = new Vector3f(arena, axis);

                    Vector3f toOther = new Vector3f(arena, other.position).subtract(this.position);
                    if (axis.dot(toOther) > 0) {
                        smallestAxis.scale(-1);
                    }
                }
            }

            Vector3f normal = smallestAxis;

            int refAxisIdx = 0;
            float maxDot = -1;
            for (int i = 0; i < 3; i++) {
                float dot = Math.abs(basisA[i].dot(normal));
                if (dot > maxDot) {
                    maxDot = dot;
                    refAxisIdx = i;
                }
            }
            Vector3f refFaceNormal = new Vector3f(arena, basisA[refAxisIdx]);
            if (refFaceNormal.dot(normal) < 0) refFaceNormal.scale(-1);

            List<Vector3f> incidentVertices = other.getFaceVertices(arena, new Vector3f(arena, refFaceNormal).scale(-1));

            int side1Idx = (refAxisIdx + 1) % 3;
            int side2Idx = (refAxisIdx + 2) % 3;
            Vector3f side1 = basisA[side1Idx];
            Vector3f side2 = basisA[side2Idx];

            List<Vector3f> clipped = incidentVertices;
            clipped = clip(arena, clipped, side1, side1.dot(this.position) + getExtent(side1Idx));
            clipped = clip(arena, clipped, new Vector3f(arena, side1).scale(-1), -side1.dot(this.position) + getExtent(side1Idx));
            clipped = clip(arena, clipped, side2, side2.dot(this.position) + getExtent(side2Idx));
            clipped = clip(arena, clipped, new Vector3f(arena, side2).scale(-1), -side2.dot(this.position) + getExtent(side2Idx));

            List<Vector3f> manifold = new ArrayList<>();
            float refFaceOffset = refFaceNormal.dot(this.position) + getExtent(refAxisIdx);
            for (Vector3f p : clipped) {
                float depth = refFaceOffset - p.dot(refFaceNormal);
                if (depth >= 0) manifold.add(new Vector3f(p));
            }

            return new SATResult(true, new Vector3f(normal), minPenetration, manifold);
        }
    }

    private List<Vector3f> clip(Arena arena, List<Vector3f> points, Vector3f planeNormal, float planeOffset) {
        List<Vector3f> result = new ArrayList<>();
        if (points.isEmpty()) return result;
        Vector3f prev = points.getLast();
        for (Vector3f curr : points) {
            float dPrev = prev.dot(planeNormal) - planeOffset;
            float dCurr = curr.dot(planeNormal) - planeOffset;
            if (dCurr <= 0) {
                if (dPrev > 0) result.add(intersect(arena, prev, curr, dPrev, dCurr));
                result.add(curr);
            } else if (dPrev <= 0) {
                result.add(intersect(arena, prev, curr, dPrev, dCurr));
            }
            prev = curr;
        }
        return result;
    }

    private Vector3f intersect(Arena arena, Vector3f a, Vector3f b, float d1, float d2) {
        return new Vector3f(arena, a).add(new Vector3f(arena, b).subtract(a).scale(d1 / (d1 - d2)));
    }
}
