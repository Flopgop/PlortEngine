package net.flamgop.borked;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.ConstShape;
import com.github.stephengold.joltjni.readonly.QuatArg;
import net.flamgop.borked.math.Matrix4f;
import net.flamgop.borked.math.Quaternionf;
import net.flamgop.borked.math.Vector3f;
import net.flamgop.borked.physics.PhysicsContext;
import net.flamgop.borked.renderer.PlortCommandBuffer;
import net.flamgop.borked.renderer.memory.BufferUsage;
import net.flamgop.borked.renderer.memory.MappedMemory;
import net.flamgop.borked.renderer.memory.PlortAllocator;
import net.flamgop.borked.renderer.memory.PlortBuffer;
import net.flamgop.borked.model.PlortMesh;
import net.flamgop.borked.model.PlortModel;
import net.flamgop.borked.renderer.pipeline.PlortPipelineLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

@SuppressWarnings("resource")
public class Entity implements AutoCloseable {
    private static final int INSTANCE_BUFFER_SIZE = 2 * Matrix4f.BYTES; // model, inverse_model

    private final PlortModel model;
    private final PlortBuffer instanceBuffer;

    private final Matrix4f transform = new Matrix4f().identity();
    private boolean transformDirty = true;

    private final PhysicsContext physicsContext;
    private final List<Body> bodies = new ArrayList<>();

    private final boolean dynamic;

    public Entity(PhysicsContext physics, PlortModel model, PlortAllocator allocator) {
        this.physicsContext = physics;
        this.model = model;
        this.dynamic = model.childAABBs().size() == 1;

        Quaternionf identity = new Quaternionf();
        for (PlortMesh child : model.childMeshes()) {
            ConvexShapeSettings shapeSettings;
            if (dynamic) {
                Vector3f halfExtents = child.aabb().size().scale(0.5f);
                shapeSettings = new BoxShapeSettings(halfExtents.x(), halfExtents.y(), halfExtents.z());
            } else {
                shapeSettings = new ConvexHullShapeSettings(child.vertices().stream().map(Vector3f::toJoltVec3).toList());
            }
            shapeSettings.setDensity(150);
            ConstShape shape = shapeSettings.create().get();

            BodyCreationSettings settings = new BodyCreationSettings(shape, dynamic ? child.aabb().center().toJoltVec3().toRVec3() : new RVec3(), identity.toJoltQuat(), dynamic ? EMotionType.Dynamic : EMotionType.Static, 0);
            settings.setFriction(0.5f);
            Body body = physics.addBody(settings, EActivation.Activate);
            bodies.add(body);
        }

        this.instanceBuffer = new PlortBuffer(INSTANCE_BUFFER_SIZE, BufferUsage.STORAGE_BUFFER_BIT, allocator);

        if (dynamic && !bodies.isEmpty()) {
            Body body = bodies.getFirst();
            Vector3f pos = new Vector3f(body.getPosition());
            transform.identity().translate(pos.x(), pos.y(), pos.z());
        }
    }

    public List<Body> bodies() {
        return Collections.unmodifiableList(bodies);
    }

    public Matrix4f transform() {
        return new Matrix4f(transform);
    }

    public void uploadTransform() {
        try (MappedMemory mem = this.instanceBuffer.map()) {
            mem.putMatrix4f(new Matrix4f(transform));
            mem.putMatrix4f(new Matrix4f(transform).invert());
        }
    }

    private void syncPhysicsFromTransform(PhysicsContext physics) {
        Vector3f pos = transform.position();
        Quaternionf rot = transform.rotation();

        RVec3 joltPos = pos.toJoltVec3().toRVec3();
        QuatArg joltRot = rot.toJoltQuat();

        BodyInterface bodyInterface = physics.system().getBodyInterface();

        for (Body body : bodies) {
            bodyInterface.setPositionAndRotation(body.getId(), joltPos, joltRot, EActivation.Activate);
        }
        transformDirty = true;
    }

    public void setPosition(Vector3f position) {
        transform.setTranslation(position.x(), position.y(), position.z());
        syncPhysicsFromTransform(this.physicsContext);
    }

    public void rotation(Quaternionf quaternionf) {
        Vector3f position = transform.position();
        transform.identity().rotate(quaternionf).translate(position);

        syncPhysicsFromTransform(this.physicsContext);
    }

    public void modifyTransform(Consumer<Matrix4f> modifier) {
        modifier.accept(transform);
        syncPhysicsFromTransform(this.physicsContext);
    }

    public PlortModel model() {
        return model;
    }

    public void submit(PlortCommandBuffer cmdBuffer, PlortPipelineLayout pipelineLayout, int currentFrameModInFlight, boolean shadow) {
        if (transformDirty) {
            uploadTransform();
            transformDirty = false;
        }
        model.submit(cmdBuffer, pipelineLayout, instanceBuffer, 1, currentFrameModInFlight, shadow);
    }

    public void update(float deltaTime) {
        if (dynamic && !bodies.isEmpty()) {
            Body body = bodies.getFirst();
            Vector3f p = new Vector3f(body.getPosition());
            Quaternionf r = new Quaternionf(body.getRotation());

            transform.identity();
            transform.translate(p.x(), p.y(), p.z());
            transform.rotate(r.normalize());

            transformDirty = true;
        }
    }

    @Override
    public void close() {
        instanceBuffer.close();
    }
}
