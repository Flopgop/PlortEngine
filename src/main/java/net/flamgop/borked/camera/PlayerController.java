package net.flamgop.borked.camera;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EGroundState;
import com.github.stephengold.joltjni.readonly.ConstAaBox;
import com.github.stephengold.joltjni.readonly.ConstMotionProperties;
import com.github.stephengold.joltjni.readonly.Vec3Arg;
import net.flamgop.borked.World;
import net.flamgop.borked.math.*;
import net.flamgop.borked.physics.next.Layers;
import net.flamgop.borked.physics.next.PhysicsContext;
import net.flamgop.borked.renderer.PlortRenderContext;
import net.flamgop.borked.model.PlortModel;
import net.flamgop.borked.renderer.window.PlortInput;
import net.flamgop.borked.renderer.window.PlortWindow;
import net.flamgop.borked.renderer.memory.BufferUsage;
import net.flamgop.borked.renderer.memory.MappedMemory;
import net.flamgop.borked.renderer.memory.PlortBuffer;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerController implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerController.class);
    public static final int VIEW_SIZE = 5 * Matrix4f.BYTES + 4 * Float.BYTES;

    public enum CameraMode {
        FOLLOWING,
        FIXED
    }

    private final PlortInput input;
    private final PlortBuffer viewBuffer;

    private final PlortBuffer shadowBuffer;
    private final Matrix4f shadowView = new Matrix4f();
    private final Matrix4f shadowProjection = new Matrix4f();
    private final Vector3f lightPos = new Vector3f();
    private final Vector3f targetLightPos = new Vector3f();
    private final float lightSmoothSpeed = 0.0005f;

    private final PlortBuffer instanceBuffer;
    private final PlortModel model;

    private final Vector3f velocity = new Vector3f(0);

    private final Vector3f up = new Vector3f(0,1,0);
    private final Vector3f position = new Vector3f(-0.5f,10,-0.5f);
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f view = new Matrix4f();

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

    private final Vector3f currentCameraPos = new Vector3f(); // For smooth interpolation
    private final Vector3f cameraTargetPos = new Vector3f();
    private float currentDistance = 5.0f;

    private CameraMode cameraMode = CameraMode.FOLLOWING;
    private final Vector3f lockedPosition = new Vector3f();

    private boolean grounded, hasDoubleJumped = false, noclip = false;
    private float lastMouseX, lastMouseY;
    private float cameraYaw, playerYaw, pitch;

    public PlayerController(PhysicsContext physicsContext, PlortRenderContext context, PlortWindow window, float fov, float sensitivity) {
        this.input = window.input();
        this.viewBuffer = new PlortBuffer(VIEW_SIZE, BufferUsage.UNIFORM_BUFFER_BIT, context.allocator());
        this.fov = fov;
        this.sensitivity = sensitivity;
        this.model = new PlortModel(context, "player.glb");
        this.instanceBuffer = new PlortBuffer(2 * Matrix4f.BYTES, BufferUsage.STORAGE_BUFFER_BIT, context.allocator());
        this.shadowBuffer = new PlortBuffer(VIEW_SIZE, BufferUsage.UNIFORM_BUFFER_BIT, context.allocator());
        this.resize(window.width(), window.height());

        CharacterVirtualSettings settings = new CharacterVirtualSettings();
        settings.setShape(new BoxShape(halfWidth, halfHeight, halfWidth));
        settings.setSupportingVolume(new Plane(new Vec3(0, 1, 0), -halfHeight));
        settings.setMaxSlopeAngle((float) Math.toRadians(45));
        settings.setInnerBodyLayer(Layers.PLAYER);
        settings.setMass(56);
        settings.setMaxStrength(280);
        character = new CharacterVirtual(settings, position.toJoltVec3().toRVec3(), new Quaternionf().identity().toJoltQuat(), 0, physicsContext.system());

        character.setListener(new CustomCharacterContactListener() {
            private final Vector3f mathVector = new Vector3f();
            @Override
            public void onContactAdded(long characterVa, int bodyId2, int subShapeId2, double contactLocationX, double contactLocationY, double contactLocationZ, float contactNormalX, float contactNormalY, float contactNormalZ, long settingsVa) {
                LOGGER.debug("Added character contact at {} {} {} with normal {} {} {}, settings va is {}", contactLocationX, contactLocationY, contactLocationZ, contactNormalX, contactNormalY, contactNormalZ, settingsVa);

                if (characterVa != character.va()) throw new IllegalStateException("Our character contact generator somehow generated a contact for a *different* character");

                BodyLockRead lock = new BodyLockRead(physicsContext.bodyLockInterface(), bodyId2);
                if (lock.succeeded()) {
                    // J = (-(1+e)(v * n)) / (1/m_1 + 1/m_2)
                    ConstMotionProperties motionProperties = lock.getBody().getMotionProperties();
                    if (motionProperties != null) {
                        float restitution = 0.1f * lock.getBody().getRestitution();
                        float numerator = -(1 + restitution) * velocity.dot(mathVector.set(contactNormalX, contactNormalY, contactNormalZ));
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

    private void uploadShadowBuffer() {
        Vector3f up = new Vector3f(0, 1, 0);
        shadowView.identity().lookAt(lightPos, new Vector3f(0), up);

        float orthoHalfSize = 25.0f;
        float near = 0.01f;
        float far = 50f;
        shadowProjection.setFrom(new Matrix4f().orthographic(
                -orthoHalfSize, orthoHalfSize,
                -orthoHalfSize, orthoHalfSize,
                near, far,
                true
        ));
        Matrix4f viewProj = new Matrix4f(shadowProjection).multiply(shadowView);

        try (MappedMemory mem = shadowBuffer.map()) {
            mem.putMatrix4f(viewProj);
            mem.putMatrix4f(shadowView);
            mem.putMatrix4f(shadowProjection);
            mem.putMatrix4f(new Matrix4f(shadowView).invert());
            mem.putMatrix4f(new Matrix4f(shadowProjection).invert());
            mem.putVector3f(lightPos);
            mem.putFloat(0f);
        }
    }

    public PlortBuffer shadowBuffer() {
        return shadowBuffer;
    }

    public Vector3f position() {
        return new Vector3f(position);
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
        this.lockedPosition.setFrom(lockedPosition);
    }

    public PlortBuffer instanceBuffer() {
        return instanceBuffer;
    }

    public Vector3f cameraForwardFlat() {
        return new Vector3f((float)(Math.cos(Math.toRadians(cameraYaw))), 0, (float)(Math.sin(Math.toRadians(cameraYaw)))).normalize();
    }

    public Vector3f playerForward() {
        return new Vector3f((float)(Math.cos(Math.toRadians(playerYaw))), 0, (float)(Math.sin(Math.toRadians(playerYaw)))).normalize();
    }

    public Vector3f right() {
        return new Vector3f(cameraForward()).cross(up).normalize();
    }

    public void resize(int width, int height) {
        this.projection.identity().perspective((float) Math.toRadians(fov), (float) width / height, 0.001f, 1000.0f, true);
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
        Vector3f right = new Vector3f(forward).cross(up).normalize();

        Vector3f wishDir = new Vector3f(0);
        if (input.keyDown(GLFW.GLFW_KEY_W)) wishDir.add(forward);
        if (input.keyDown(GLFW.GLFW_KEY_S)) wishDir.subtract(forward);
        if (input.keyDown(GLFW.GLFW_KEY_A)) wishDir.subtract(right);
        if (input.keyDown(GLFW.GLFW_KEY_D)) wishDir.add(right);

        if (wishDir.lengthSquared() > 0) {
            wishDir.normalize();
            float targetYaw = (float) Math.toDegrees(Math.atan2(wishDir.x(), wishDir.z()));
            playerYaw = MathUtil.lerpfAngle(playerYaw, targetYaw, 0.005f, deltaTime);
        }

        float currentFriction = grounded ? friction : airResistance;
        float drag = 1.0f - (currentFriction * deltaTime);
        if (drag < 0) drag = 0;
        velocity.x(velocity.x() * drag);
        velocity.z(velocity.z() * drag);

        velocity.x(velocity.x() + wishDir.x() * acceleration * deltaTime);
        velocity.z(velocity.z() + wishDir.z() * acceleration * deltaTime);

        float horizSpeed = (float) Math.sqrt(velocity.x() * velocity.x() + velocity.z() * velocity.z());
        if (horizSpeed > maxSpeed) {
            velocity.x((velocity.x() / horizSpeed) * maxSpeed);
            velocity.z((velocity.z() / horizSpeed) * maxSpeed);
        }

        if (input.keyPressed(GLFW.GLFW_KEY_F)) {
            noclip = !noclip;
        }

        if (input.keyPressed(GLFW.GLFW_KEY_SPACE)) {
            if (grounded) {
                velocity.y(jumpForce);
                grounded = false;
                hasDoubleJumped = false;
            } else if (!hasDoubleJumped) {
                velocity.y(jumpForce);
                hasDoubleJumped = true;
            }
        }
    }

    public void physicsStep(PhysicsContext context, float dt) {
        this.grounded = noclip || this.character.getGroundState() == EGroundState.OnGround;
        if (grounded) {
            hasDoubleJumped = false;
            if (velocity.y() < 0) {
                velocity.y(0);
            }
        } else {
            velocity.y(velocity.y() + gravity * dt);
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
            character.setPosition(new Vector3f(character.getPosition()).add(new Vector3f(velocity).scale(dt)).toJoltVec3().toRVec3());
        }

        position.setFrom(character.getPosition());
    }

    private float resolveCameraCollision(PhysicsContext context, Vector3f pivot, Vector3f dirToCamera) {
        float maxDist = targetDistance;
        float margin = 0.4f;

        RVec3 rayOrigin = pivot.toJoltVec3().toRVec3();
        Vec3Arg rayDirection = new Vector3f(dirToCamera).scale(maxDist).toJoltVec3();
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

    private void upload() {
        try (MappedMemory mem = viewBuffer.map()) {
            mem.putMatrix4f(new Matrix4f(projection).multiply(view));
            mem.putMatrix4f(view);
            mem.putMatrix4f(projection);
            mem.putMatrix4f(new Matrix4f(view).invert());
            mem.putMatrix4f(new Matrix4f(projection).invert());
            mem.putVector3f(currentCameraPos);
            mem.putFloat(0f);
        }
        try (MappedMemory mem = instanceBuffer.map()) {
            Matrix4f transform = new Matrix4f();

            float half = (float) Math.toRadians(playerYaw) * 0.5f;
            transform.rotate(new Quaternionf(0, (float) Math.sin(half), 0, (float) Math.cos(half)));

            transform.translate(new Vector3f(character.getPosition()).subtract(0,halfHeight,0));

            mem.putMatrix4f(transform);
            mem.putMatrix4f(transform.invert());
        }
        uploadShadowBuffer();
    }

    public void update(World world, PhysicsContext physicsContext, float deltaTime) {
        if (cameraMode == CameraMode.FOLLOWING) {
            look();
        }
        physicsStep(physicsContext, deltaTime);
        if (grounded) {
            targetLightPos.setFrom(world.lightPos());
        } else {
            targetLightPos.set(0, 25f, 0f);
        }
        lightPos.lerp(targetLightPos, lightSmoothSpeed, deltaTime);

        cameraTargetPos.lerp(position, targetFollowSpeed, deltaTime);

        Vector3f pivot = new Vector3f(cameraTargetPos).add(0, headOffset, 0);
        Vector3f desiredCameraPos = new Vector3f();

        if (cameraMode == CameraMode.FOLLOWING) {
            Vector3f dirToCamera = cameraForward().scale(-1);

            Vector3f rayPivot = new Vector3f(position).add(0, headOffset, 0);
            float targetDist = resolveCameraCollision(physicsContext, rayPivot, dirToCamera);

            if (targetDist < currentDistance) {
                currentDistance = targetDist;
            } else {
                currentDistance = MathUtil.lerpf(currentDistance, targetDist, smoothSpeed, deltaTime);
            }

            desiredCameraPos.setFrom(dirToCamera).scale(currentDistance).add(pivot);
        } else {
            desiredCameraPos.setFrom(lockedPosition);
        }

        currentCameraPos.lerp(desiredCameraPos, smoothSpeed, deltaTime);

        view.identity().lookAt(currentCameraPos, pivot, up);

        upload();
    }

    public PlortBuffer viewBuffer() {
        return viewBuffer;
    }

    @Override
    public void close() {
        this.model.close();
        this.instanceBuffer.close();
        this.shadowBuffer.close();
        this.viewBuffer.close();
    }
}
