package net.flamgop.borked.entity.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.flamgop.borked.math.Matrix4f;

public final class Transform {
    public static final Codec<Transform> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Matrix4f.CODEC.fieldOf("matrix").forGetter(Transform::transform)
            ).apply(instance, Transform::new)
    );
    private final Matrix4f transform = new Matrix4f();
    private boolean dirty = true;

    public Transform(Matrix4f values) {
        transform.setFrom(values);
    }

    public Transform() {}

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
