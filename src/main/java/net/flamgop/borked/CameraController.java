package net.flamgop.borked;

import net.flamgop.borked.math.*;
import net.flamgop.borked.renderer.model.PlortModel;
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

    private final Vector3f velocity = new Vector3f(0);

    private final Vector3f up = new Vector3f(0,1,0);
    private final Vector3f position = new Vector3f(-0.5f,10,-0.5f);
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f view = new Matrix4f();

    private final float fov;
    private final float sensitivity;

    private final float halfWidth = 0.5f;
    private final float halfHeight = 1f;
    private final AABB aabb = new AABB(new Vector3f(-halfWidth,-halfHeight,-halfWidth), new Vector3f(halfWidth, halfHeight, halfWidth));

    private final float gravity = -20f;
    private final float jumpForce = 10f;
    private final float cameraOffset = 0.85f;

    private boolean grounded, applyGravity = false;
    private float lastMouseX, lastMouseY;
    private float yaw, pitch;

    public CameraController(PlortAllocator allocator, PlortWindow window, float fov, float sensitivity) {
        this.input = window.input();
        this.viewBuffer = new PlortBuffer(5 * Matrix4f.BYTES + 4 * Float.BYTES, BufferUsage.UNIFORM_BUFFER_BIT, allocator);
        this.fov = fov;
        this.sensitivity = sensitivity;
        aabb.translate(position);
        this.resize(window.width(), window.height());
    }

    public Vector3f cameraForward() {
        return new Vector3f(
                (float)(Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch))),
                (float) Math.sin(Math.toRadians(pitch)),
                (float)(Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)))
        ).normalize();
    }

    public Vector3f playerForward() {
        return new Vector3f((float)(Math.cos(Math.toRadians(yaw))), 0, (float)(Math.sin(Math.toRadians(yaw)))).normalize();
    }

    public Vector3f right() {
        return new Vector3f(cameraForward()).cross(up).normalize();
    }

    public void resize(int width, int height) {
        this.projection.setIdentity().perspective((float) Math.toRadians(fov), (float) width / height, 0.001f, 1000.0f, true);
    }

    public AABB aabb() {
        return aabb;
    }

    private void look() {
        Vector2f mousePos = input.mousePosition();

        float dx = (mousePos.x() - lastMouseX) * sensitivity;
        float dy = (lastMouseY - mousePos.y()) * sensitivity;

        lastMouseX = mousePos.x();
        lastMouseY = mousePos.y();

        yaw += dx;
        pitch += dy;

        pitch = Math.max(-89f, Math.min(89f, pitch));
    }

    private void move(float deltaTime) {
        Vector3f forward = playerForward();
        Vector3f right = new Vector3f(forward).cross(up).normalize();

        velocity.x(0);
        velocity.z(0);

        float speed = 2f;

        if (input.keyDown(GLFW.GLFW_KEY_W)) velocity.add(new Vector3f(forward).scale(speed));
        if (input.keyDown(GLFW.GLFW_KEY_A)) velocity.add(new Vector3f(right).scale(-speed));
        if (input.keyDown(GLFW.GLFW_KEY_S)) velocity.add(new Vector3f(forward).scale(-speed));
        if (input.keyDown(GLFW.GLFW_KEY_D)) velocity.add(new Vector3f(right).scale(speed));

        if (input.keyPressed(GLFW.GLFW_KEY_F)) applyGravity = !applyGravity;

        if (!grounded) {
            velocity.y(velocity.y() + gravity * deltaTime);
        }

        if (input.keyDown(GLFW.GLFW_KEY_SPACE) && grounded) {
            grounded = false;
            velocity.y(jumpForce);
        }
    }

    public void physicsStep(World world, float dt) {
        grounded = false;
        Vector3f delta = new Vector3f(velocity).scale(dt);

        aabb.translate(new Vector3f(delta.x(), 0, 0));
        resolveAxis(world, Axis.X);

        aabb.translate(new Vector3f(0, delta.y(), 0));
        resolveAxis(world, Axis.Y);

        if (!applyGravity && aabb.min().y() < 0) {
            float penetration = -aabb.min().y();
            aabb.translate(new Vector3f(0, penetration, 0));
            velocity.y(0);
            grounded = true;
        }

        aabb.translate(new Vector3f(0, 0, delta.z()));
        resolveAxis(world, Axis.Z);

        position.setFrom(aabb.center());
    }

    private void resolveAxis(World world, Axis axis) {
        for (Entity e : world.entities) {
            Vector3f position = e.transform().position();
            PlortModel model = e.model();

            for (AABB child : model.childAABBs()) {
                if (!child.hasCollision()) continue;
                AABB worldChild = child.translated(position);
                if (!aabb.intersects(worldChild)) continue;

                Vector3f resolution = aabb.resolveAxis(worldChild, axis);
                aabb.translate(resolution);

                switch (axis) {
                    case X -> velocity.x(0);
                    case Z -> velocity.z(0);
                    case Y -> {
                        if (resolution.y() > 0) {
                            grounded = true;
                            velocity.y(0);
                        }
                    }
                }
            }
        }
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

    public void update(World world, float deltaTime) {
        look();
        move(deltaTime);
        physicsStep(world, deltaTime);

        Vector3f offsetPosition = new Vector3f(position).add(0, cameraOffset, 0);
        Vector3f cameraTarget = new Vector3f(offsetPosition).add(cameraForward());
        view.setIdentity().lookAt(offsetPosition, cameraTarget, up);

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
