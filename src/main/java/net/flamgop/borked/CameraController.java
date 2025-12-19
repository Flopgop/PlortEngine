package net.flamgop.borked;

import net.flamgop.borked.math.Matrix4f;
import net.flamgop.borked.math.Vector2f;
import net.flamgop.borked.math.Vector3f;
import net.flamgop.borked.renderer.window.PlortInput;
import net.flamgop.borked.renderer.window.PlortWindow;
import net.flamgop.borked.renderer.memory.BufferUsage;
import net.flamgop.borked.renderer.memory.MappedMemory;
import net.flamgop.borked.renderer.memory.PlortAllocator;
import net.flamgop.borked.renderer.memory.PlortBuffer;
import org.lwjgl.glfw.GLFW;

public class CameraController implements AutoCloseable {
    private final PlortInput input;
    private final PlortBuffer viewBuffer;

    private final Vector3f up = new Vector3f(0,1,0);
    private final Vector3f position = new Vector3f(0,0,0);
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f view = new Matrix4f();

    private final float fov;
    private final float sensitivity;

    private float lastMouseX, lastMouseY;
    private float yaw, pitch;

    public CameraController(PlortAllocator allocator, PlortWindow window, float fov, float sensitivity) {
        this.input = window.input();
        this.viewBuffer = new PlortBuffer(5 * Matrix4f.BYTES + 4 * Float.BYTES, BufferUsage.UNIFORM_BUFFER_BIT, allocator);
        this.fov = fov;
        this.sensitivity = sensitivity;
        this.resize(window.width(), window.height());
    }

    public Vector3f forward() {
        return new Vector3f(
                (float)(Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch))),
                (float) Math.sin(Math.toRadians(pitch)),
                (float)(Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)))
        ).normalize();
    }

    public Vector3f right() {
        return new Vector3f(forward()).cross(up).normalize();
    }

    public void resize(int width, int height) {
        this.projection.setIdentity().perspective((float) Math.toRadians(fov), (float) width / height, 0.001f, 1000.0f, true);
    }

    private void look() {
        Vector2f mousePos = input.mousePosition();

        float dx = ((float) mousePos.x() - lastMouseX) * sensitivity;
        float dy = (lastMouseY - (float) mousePos.y()) * sensitivity;

        lastMouseX = (float) mousePos.x();
        lastMouseY = (float) mousePos.y();

        yaw += dx;
        pitch += dy;

        pitch = Math.max(-89f, Math.min(89f, pitch));
    }
    private void move(float deltaTime) {
        Vector3f forward = forward();
        Vector3f right = new Vector3f(forward).cross(up).normalize();

        float speed = 2f * deltaTime;

        if (input.keyDown(GLFW.GLFW_KEY_W)) {
            Vector3f displacement = new Vector3f(forward).scale(speed);
            position.add(displacement);
        }
        if (input.keyDown(GLFW.GLFW_KEY_A)) {
            Vector3f displacement = new Vector3f(right).scale(-speed);
            position.add(displacement);
        }
        if (input.keyDown(GLFW.GLFW_KEY_S)) {
            Vector3f displacement = new Vector3f(forward).scale(-speed);
            position.add(displacement);
        }
        if (input.keyDown(GLFW.GLFW_KEY_D)) {
            Vector3f displacement = new Vector3f(right).scale(speed);
            position.add(displacement);
        }

        Vector3f cameraTarget = new Vector3f(position).add(forward);
        view.setIdentity().lookAt(position, cameraTarget, up);
    }
    private void upload() {
        try (MappedMemory mem = viewBuffer.map()) {
            mem.putMatrix4f(new Matrix4f(projection).multiply(view));
            mem.putMatrix4f(view);
            mem.putMatrix4f(projection);
            mem.putMatrix4f(new Matrix4f(view).invert());
            mem.putMatrix4f(new Matrix4f(projection).invert());
            mem.putFloat(position.x());
            mem.putFloat(position.y());
            mem.putFloat(position.z());
            mem.putFloat(0f);
        }
    }

    public void update(float deltaTime) {
        look();
        move(deltaTime);
        upload();
    }

    public PlortBuffer viewBuffer() {
        return viewBuffer;
    }

    @Override
    public void close() {
        this.viewBuffer.close();
    }
}
