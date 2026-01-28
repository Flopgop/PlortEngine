package net.flamgop.borked.entity.components;

import net.flamgop.borked.math.Matrix4f;

public final class Transform {
    private final Matrix4f transform = new Matrix4f();
    private boolean dirty = true;

    public void transform(Matrix4f matrix) {
        dirty = true;
        transform.setFrom(matrix);
    }

    public Matrix4f transform() {
        return transform;
    }

    public boolean dirty() {
        return dirty;
    }
}
