package net.flamgop.plort.engine.camera;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.Plane;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EGroundState;
import com.github.stephengold.joltjni.readonly.ConstAaBox;
import com.github.stephengold.joltjni.readonly.ConstMotionProperties;
import com.github.stephengold.joltjni.readonly.QuatArg;
import com.github.stephengold.joltjni.readonly.Vec3Arg;
import net.flamgop.borked.math.*;
import net.flamgop.borked.math.val.*;
import net.flamgop.plort.engine.math.MathUtil;
import net.flamgop.plort.engine.math.val.*;
import net.flamgop.plort.engine.physics.Layers;
import net.flamgop.plort.engine.physics.PhysicsContext;
import net.flamgop.plort.engine.renderer.PlortCommandBuffer;
import net.flamgop.plort.engine.renderer.PlortRenderContext;
import net.flamgop.plort.engine.model.PlortModel;
import net.flamgop.plort.engine.renderer.descriptor.PlortBufferedDescriptorSetPool;
import net.flamgop.plort.engine.renderer.memory.BufferedObject;
import net.flamgop.plort.engine.renderer.pipeline.PlortPipelineLayout;
import net.flamgop.plort.engine.renderer.window.PlortInput;
import net.flamgop.plort.engine.renderer.window.PlortWindow;
import net.flamgop.plort.engine.renderer.memory.BufferUsage;
import net.flamgop.plort.engine.renderer.memory.MappedMemory;
import net.flamgop.plort.engine.renderer.memory.PlortBuffer;
import net.flamgop.plort.engine.resource.ResourceIdentifier;
import net.flamgop.plort.engine.resource.ResourceManager;
import net.flamgop.plort.engine.world.SceneData;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("resource")
public class PlayerController implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerController.class);
    public static final int VIEW_SIZE = 5 * Matrix4f.BYTES + 4 * Float.BYTES;

    public enum CameraMode {
        FOLLOWING,
        FIXED
    }

    private final PlortInput input;
    private final BufferedObject<PlortBuffer> viewBuffers;

    private Vector3f targetLightPos;

    private final BufferedObject<PlortBuffer> instanceBuffers;
    private final PlortModel model;
    private final PlortBufferedDescriptorSetPool descriptorSetPool;
    private final PlortBufferedDescriptorSetPool shadowDescriptorSetPool;

    private Vector3f velocity = new Vector3f(0);

    private final Vector3f up = new Vector3f(0,1,0);
    private Vector3f position = new Vector3f(-0.5f,0,-0.5f);
    private Matrix4f projection = new Matrix4f();
    private Matrix4f view = new Matrix4f();

    private final float fov;
    private final float sensitivity;

    private final float halfWidth = 0.5f;
    private final float halfHeight = 1f;

    private final CharacterVirtual character;
    private final BodyFilter allButMeFilter;

    private final float gravity = -20f;
    private final float jumpForce = 8f;
    private final float headOffset = 0.85f;
    private final float targetDistance = 5.0f;
    private final float smoothSpeed = 0.0001f;
    private final float targetFollowSpeed = 0.0005f;
    private final float acceleration = 100f;
    private final float friction = 10f;
    private final float maxSpeed = 10f;
    private final float airResistance = 4f;

    private Vector3f currentCameraPos = new Vector3f(); // For smooth interpolation
    private Vector3f cameraTargetPos = new Vector3f();
    private float currentDistance = 5.0f;

    private CameraMode cameraMode = CameraMode.FOLLOWING;
    private Vector3f lockedPosition = new Vector3f();

    private boolean grounded, hasDoubleJumped = false, noclip = false;
    private float lastMouseX, lastMouseY;
    private float cameraYaw, playerYaw, pitch;

    public PlayerController(ResourceManager resourceManager, PhysicsContext physicsContext, PlortRenderContext context, PlortWindow window, float fov, float sensitivity) {
        this.input = window.input();
        this.viewBuffers = new BufferedObject<>(PlortBuffer.class, context.swapchain().imageCount(), _ -> new PlortBuffer(VIEW_SIZE, BufferUsage.UNIFORM_BUFFER_BIT, context.allocator()));
        this.fov = fov;
        this.sensitivity = sensitivity;
        this.model = new PlortModel(context, resourceManager, new ResourceIdentifier("borked", "model/player.glb"));
        this.descriptorSetPool = new PlortBufferedDescriptorSetPool(context.device(), model.layout(), model.materialCount(), context.swapchain().imageCount());
        this.shadowDescriptorSetPool = new PlortBufferedDescriptorSetPool(context.device(), model.layout(), model.materialCount(), context.swapchain().imageCount());
        this.instanceBuffers = new BufferedObject<>(PlortBuffer.class, context.swapchain().imageCount(), _ -> new PlortBuffer(2 * Matrix4f.BYTES, BufferUsage.STORAGE_BUFFER_BIT, context.allocator()));
        this.resize(window.width(), window.height());

        model.writeDescriptors(context, descriptorSetPool);
        model.writeDescriptors(context, shadowDescriptorSetPool);

        CharacterVirtualSettings settings = new CharacterVirtualSettings();
        settings.setShape(new BoxShape(halfWidth, halfHeight, halfWidth));
        settings.setSupportingVolume(new Plane(new Vec3(0, 1, 0), -halfHeight));
        settings.setMaxSlopeAngle((float) Math.toRadians(45));
        settings.setInnerBodyLayer(Layers.PLAYER);
        settings.setMass(56);
        settings.setMaxStrength(280);
        character = new CharacterVirtual(settings, position.toJoltVec3().toRVec3(), new Quaternionf().toJoltQuat(), 0, physicsContext.system());

        Quaternionf rot = new Quaternionf();

        RVec3 joltPos = position.toJoltVec3().toRVec3();
        QuatArg joltRot = rot.toJoltQuat();

        BodyInterface bodyInterface = physicsContext.system().getBodyInterface();

        bodyInterface.setPositionAndRotation(character.getInnerBodyId(), joltPos, joltRot, EActivation.Activate);


        character.setListener(new CustomCharacterContactListener() {
            @Override
            public void onContactAdded(long characterVa, int bodyId2, int subShapeId2, double contactLocationX, double contactLocationY, double contactLocationZ, float contactNormalX, float contactNormalY, float contactNormalZ, long settingsVa) {
                if (characterVa != character.va()) throw new IllegalStateException("Our character contact generator somehow generated a contact for a *different* character");

                BodyLockRead lock = new BodyLockRead(physicsContext.bodyLockInterface(), bodyId2);
                if (lock.succeeded()) {
                    // J = (-(1+e)(v * n)) / (1/m_1 + 1/m_2)
                    ConstMotionProperties motionProperties = lock.getBody().getMotionProperties();
                    if (motionProperties != null) {
                        float restitution = 0.1f * lock.getBody().getRestitution();
                        float numerator = -(1 + restitution) * velocity.dot(new Vector3f(contactNormalX, contactNormalY, contactNormalZ));
                        float j = numerator / (1 / character.getMass() + motionProperties.getInverseMass());

                        Vec3 force = new Vec3(contactNormalX, contactNormalY, contactNormalZ);
                        force.scaleInPlace(-j);
                        lock.releaseLock();
                        physicsContext.system().getBodyInterfaceNoLock().addImpulse(bodyId2, force, new RVec3(contactLocationX, contactLocationY, contactLocationZ));
                    } else lock.releaseLock();
                } else {
                    lock.releaseLock();
                }
            }
        });

        IgnoreMultipleBodiesFilter filter = new IgnoreMultipleBodiesFilter();
        filter.ignoreBody(character.getInnerBodyId());
        allButMeFilter = filter;
    }

    public void setupViewBuffers(int frame, PlortBuffer shadowViewBuffer) {
        this.model.writeViewBuffer(this.viewBuffers.get(frame), m -> descriptorSetPool.descriptorSet(frame, m));
        this.model.writeViewBuffer(shadowViewBuffer, m -> shadowDescriptorSetPool.descriptorSet(frame, m));
    }

    public Frustum computeFrustum() {
        return Frustum.fromViewProjectionMatrix(projection.multiply(view), true);
    }

    public Vector3f playerPosition() {
        return position;
    }

    public Vector3f cameraPosition() {
        return currentCameraPos;
    }

    public Vector3f cameraForward() {
        return new Vector3f(
                (float)(Math.cos(Math.toRadians(cameraYaw)) * Math.cos(Math.toRadians(pitch))),
                (float) Math.sin(Math.toRadians(pitch)),
                (float)(Math.sin(Math.toRadians(cameraYaw)) * Math.cos(Math.toRadians(pitch)))
        ).normalize();
    }

    public void cameraMode(CameraMode mode) {
        this.cameraMode = mode;
    }

    public void lockedPosition(Vector3f lockedPosition) {
        this.lockedPosition = lockedPosition;
    }

    public PlortBuffer instanceBuffer(int imageIndex) {
        return instanceBuffers.get(imageIndex);
    }

    public Vector3f cameraForwardFlat() {
        return new Vector3f((float)(Math.cos(Math.toRadians(cameraYaw))), 0, (float)(Math.sin(Math.toRadians(cameraYaw)))).normalize();
    }

    public Vector3f playerForward() {
        return new Vector3f((float)(Math.cos(Math.toRadians(playerYaw))), 0, (float)(Math.sin(Math.toRadians(playerYaw)))).normalize();
    }

    public Vector3f right() {
        return cameraForward().cross(up).normalize();
    }

    public void resize(int width, int height) {
        this.projection = Matrix4f.perspective((float) Math.toRadians(fov), (float) width / height, 0.001f, 1000.0f, true);
    }

    public ConstAaBox aabb() {
        return character.getTransformedShape().getWorldSpaceBounds();
    }

    public PlortModel model() {
        return model;
    }

    private void look() {
        Vector2f mousePos = input.mousePosition();

        float dx = (mousePos.x() - lastMouseX) * sensitivity;
        float dy = (lastMouseY - mousePos.y()) * sensitivity;

        lastMouseX = mousePos.x();
        lastMouseY = mousePos.y();

        cameraYaw += dx;
        pitch += dy;

        pitch = Math.max(-50f, Math.min(50f, pitch));
    }

    private void move(float deltaTime) {
        Vector3f forward = cameraForwardFlat();
        Vector3f right = forward.cross(up).normalize();

        Vector3f wishDir = new Vector3f(0);
        if (input.keyDown(GLFW.GLFW_KEY_W)) wishDir = wishDir.add(forward);
        if (input.keyDown(GLFW.GLFW_KEY_S)) wishDir = wishDir.subtract(forward);
        if (input.keyDown(GLFW.GLFW_KEY_A)) wishDir = wishDir.subtract(right);
        if (input.keyDown(GLFW.GLFW_KEY_D)) wishDir = wishDir.add(right);

        if (wishDir.lengthSquared() > 0) {
            wishDir = wishDir.normalize();
            float targetYaw = (float) Math.toDegrees(Math.atan2(wishDir.x(), wishDir.z()));
            playerYaw = MathUtil.lerpfAngle(playerYaw, targetYaw, 0.005f, deltaTime);
        }

        float currentFriction = grounded ? friction : airResistance;
        float drag = 1.0f - (currentFriction * deltaTime);
        if (drag < 0) drag = 0;
        velocity = new Vector3f(
                (velocity.x() * drag) + wishDir.x() * acceleration * deltaTime,
                velocity.y(),
                (velocity.z() * drag) + wishDir.z() * acceleration * deltaTime
        );

        float horizSpeed = (float) Math.sqrt(velocity.x() * velocity.x() + velocity.z() * velocity.z());
        if (horizSpeed > maxSpeed) {
            velocity = new Vector3f(
                    (velocity.x() / horizSpeed) * maxSpeed,
                    velocity.y(),
                    (velocity.z() / horizSpeed) * maxSpeed
            );
        }

        if (input.keyPressed(GLFW.GLFW_KEY_F)) {
            noclip = !noclip;
        }

        if (input.keyPressed(GLFW.GLFW_KEY_SPACE)) {
            if (grounded) {
                velocity = new Vector3f(velocity.x(), jumpForce, velocity.z());
                grounded = false;
                hasDoubleJumped = false;
            } else if (!hasDoubleJumped) {
                velocity = new Vector3f(velocity.x(), jumpForce, velocity.z());
                hasDoubleJumped = true;
            }
        }
    }

    public void physicsStep(PhysicsContext context, float dt) {
        this.grounded = noclip || this.character.getGroundState() == EGroundState.OnGround;
        if (grounded) {
            hasDoubleJumped = false;
            if (velocity.y() < 0) {
                velocity = new Vector3f(velocity.x(), 0, velocity.z());
            }
        } else {
            velocity = new Vector3f(velocity.x(), velocity.y() + gravity * dt, velocity.z());
        }

        move(dt);

        PhysicsSystem system = context.system();

        if (input.keyPressed(GLFW.GLFW_KEY_F)) {
            context.bodyInterface().setObjectLayer(character.getInnerBodyId(), noclip ? Layers.GHOST : Layers.PLAYER);
        }

        if (!noclip) {
            character.setLinearVelocity(velocity.toJoltVec3());
            ExtendedUpdateSettings settings = new ExtendedUpdateSettings();
            settings.setWalkStairsStepUp(new Vec3());
            character.extendedUpdate(dt,
                    new Vec3(0, -gravity, 0),
                    settings,
                    system.getDefaultBroadPhaseLayerFilter(Layers.MOVING),
                    system.getDefaultLayerFilter(Layers.MOVING),
                    allButMeFilter,
                    new ShapeFilter(),
                    context.tempAllocator()
            );
        } else {
            character.setPosition(new Vector3f(character.getPosition()).add(velocity.scale(dt)).toJoltVec3().toRVec3());
        }

        position = new Vector3f(character.getPosition());
    }

    private float resolveCameraCollision(PhysicsContext context, Vector3f pivot, Vector3f dirToCamera) {
        float maxDist = targetDistance;
        float margin = 0.4f;

        RVec3 rayOrigin = pivot.toJoltVec3().toRVec3();
        Vec3Arg rayDirection = dirToCamera.scale(maxDist).toJoltVec3();
        RRayCast ray = new RRayCast(rayOrigin, rayDirection);

        RayCastResult result = new RayCastResult();
        boolean hit = context.system().getNarrowPhaseQuery().castRay(
                ray,
                result,
                new BroadPhaseLayerFilter(),
                new ObjectLayerFilter(),
                allButMeFilter
        );

        if (hit) {
            float hitDistance = result.getFraction() * maxDist;
            return Math.max(0.1f, hitDistance - margin);
        }

        return maxDist;
    }

    public void upload(int imageIndex) {
        ViewHelper.uploadViewBuffer(viewBuffers.get(imageIndex), view, projection, currentCameraPos);
        try (MappedMemory mem = instanceBuffers.get(imageIndex).map()) {
            Matrix4f transform = new Matrix4f();

            float half = (float) Math.toRadians(playerYaw) * 0.5f;
            transform = transform.rotate(new Quaternionf(0, (float) Math.sin(half), 0, (float) Math.cos(half)));

            transform = transform.translate(new Vector3f(character.getPosition()).subtract(0,halfHeight,0));

            mem.putMatrix4f(transform);
            mem.putMatrix4f(transform.invert());
        }
    }

    public void update(SceneData sceneData, PhysicsContext physicsContext, float deltaTime) {
        if (cameraMode == CameraMode.FOLLOWING) {
            look();
        }
        physicsStep(physicsContext, deltaTime);
        if (grounded) {
            targetLightPos = sceneData.lightPos();
        } else {
            targetLightPos = new Vector3f(0, 25f, 0);
        }

        cameraTargetPos = cameraTargetPos.lerp(position, targetFollowSpeed, deltaTime);

        Vector3f pivot = cameraTargetPos.add(0, headOffset, 0);
        Vector3f desiredCameraPos;

        if (cameraMode == CameraMode.FOLLOWING) {
            Vector3f dirToCamera = cameraForward().scale(-1);

            Vector3f rayPivot = position.add(0, headOffset, 0);
            float targetDist = resolveCameraCollision(physicsContext, rayPivot, dirToCamera);

            if (targetDist < currentDistance) {
                currentDistance = targetDist;
            } else {
                currentDistance = MathUtil.lerpf(currentDistance, targetDist, smoothSpeed, deltaTime);
            }

            desiredCameraPos = dirToCamera.scale(currentDistance).add(pivot);
        } else {
            desiredCameraPos = lockedPosition;
        }

        currentCameraPos = currentCameraPos.lerp(desiredCameraPos, smoothSpeed, deltaTime);

        view = Matrix4f.lookAt(currentCameraPos, pivot, up);
    }

    public void submit(PlortCommandBuffer cmdBuffer, PlortPipelineLayout pipelineLayout, int frame, boolean shadow) {
        this.model.submit(cmdBuffer, pipelineLayout, this.instanceBuffers.get(frame), shadow ? shadowDescriptorSetPool : descriptorSetPool, 1, frame, this.computeFrustum(), position);
    }

    public Vector3f targetLightPos() {
        return targetLightPos;
    }

    public PlortBuffer viewBuffer(int imageIndex) {
        return viewBuffers.get(imageIndex);
    }

    @Override
    public void close() {
        this.model.close();
        this.descriptorSetPool.close();
        this.shadowDescriptorSetPool.close();
        try {this.instanceBuffers.close();} catch (Exception _) {}
        try {this.viewBuffers.close();} catch (Exception _) {}
    }
}
