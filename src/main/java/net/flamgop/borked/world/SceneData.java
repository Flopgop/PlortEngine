package net.flamgop.borked.world;

import net.flamgop.borked.math.val.Vector3f;
import net.flamgop.borked.renderer.memory.BufferUsage;
import net.flamgop.borked.renderer.memory.MappedMemory;
import net.flamgop.borked.renderer.memory.PlortAllocator;
import net.flamgop.borked.renderer.memory.PlortBuffer;

public class SceneData implements AutoCloseable {

    private static final long SCENE_SIZE = 4 * Vector3f.BYTES + 2 * Float.BYTES;

    private final PlortBuffer buffer;

    private Vector3f lightDir = new Vector3f(-1f).normalize();
    private Vector3f lightPos = new Vector3f(0).add(lightDir.scale(-25.0f));
    private Vector3f lightColor = new Vector3f(1.0f, 0.976f, 0.937f);
    private Vector3f ambientColor = new Vector3f(0.529411765f, 0.807843137f, 0.921568627f).scale(0.1f);
    private Vector3f fogColor = new Vector3f(0f);
    private float lightIntensity = 1f;
    private float fogDensity = 0f;

    private boolean dirty = true;

    public SceneData(PlortAllocator allocator) {
        this.buffer = new PlortBuffer(SCENE_SIZE, BufferUsage.STORAGE_BUFFER_BIT, allocator);
    }

    public Vector3f lightPos() {
        return lightPos;
    }

    public void lightPos(Vector3f v) {
        lightPos = v;
    }

    public void lightDir(Vector3f v) {
        lightDir = v;
        dirty = true;
    }

    public void lightColor(Vector3f v) {
        lightColor = v;
        dirty = true;
    }

    public void lightIntensity(float f) {
        lightIntensity = f;
        dirty = true;
    }

    public void ambientColor(Vector3f v) {
        ambientColor = v;
        dirty = true;
    }

    public void fogColor(Vector3f v) {
        fogColor = v;
        dirty = true;
    }

    public void fogDensity(float f) {
        fogDensity = f;
        dirty = true;
    }

    public void upload() {
        dirty = false;
        try (MappedMemory mem = buffer.map()) {
            mem.putVector3f(lightDir); // light direction
            mem.putVector3f(lightColor); // light color
            mem.putFloat(lightIntensity); // light intensity

            mem.putVector3f(ambientColor); // ambient color

            mem.putVector3f(fogColor); // fog color
            mem.putFloat(fogDensity); // fog density
        }
    }

    public boolean dirty() {
        return dirty;
    }

    public PlortBuffer buffer() {
        return buffer;
    }

    @Override
    public void close() {
        buffer.close();
    }
}
