package net.flamgop.borked.renderer.memory;

import net.flamgop.borked.math.*;

import java.lang.reflect.RecordComponent;
import java.util.Map;

public class RecordSerializer {

    private static final Map<Class<?>, Integer> sizeMap = Map.ofEntries(
            Map.entry(Byte.class, Byte.BYTES),
            Map.entry(Short.class, Short.BYTES),
            Map.entry(Integer.class, Integer.BYTES),
            Map.entry(Float.class, Float.BYTES),
            Map.entry(Double.class, Double.BYTES),
            Map.entry(Matrix4f.class, Matrix4f.BYTES),
            Map.entry(Quaternionf.class, Quaternionf.BYTES),
            Map.entry(Vector2d.class, Vector2d.BYTES),
            Map.entry(Vector2f.class, Vector2f.BYTES),
            Map.entry(Vector2i.class, Vector2i.BYTES),
            Map.entry(Vector3f.class, Vector3f.BYTES),
            Map.entry(Vector3i.class, Vector3i.BYTES),
            Map.entry(Vector4d.class, Vector4d.BYTES)
    );

    public static long calculateSize(Class<? extends Record> recordClass) {
        RecordComponent[] components = recordClass.getRecordComponents();
        long sum = 0;
        for (RecordComponent component : components) {
            sum += sizeMap.get(component.getType());
        }
        return sum;
    }
}
