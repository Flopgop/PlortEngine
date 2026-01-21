package net.flamgop.borked;

import com.github.stephengold.joltjni.readonly.ConstAaBox;
import net.flamgop.borked.camera.PlayerController;
import net.flamgop.borked.math.Vector3f;
import net.flamgop.borked.physics.PhysicsContext;
import net.flamgop.borked.renderer.PlortRenderContext;
import net.flamgop.borked.renderer.memory.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class World implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(World.class);
    private static final boolean DRAW_PLAYER_AABB = true;

    private static final long SCENE_SIZE = 4 * Vector3f.BYTES + 2 * Float.BYTES;
    private static final long AABB_SIZE = 2 * Vector3f.BYTES + 2 * Float.BYTES;
    private final PlortAllocator allocator;
    private long aabbCount;
    private final BufferedObject<PlortBuffer> aabbBuffer;

    private final PlortBuffer sceneData;

    private final PhysicsContext physicsContext;
    private final PlortRenderContext renderContext;
    private ShadowManager shadowManager;

    protected final List<Entity> entities = new ArrayList<>();
    private final PlayerController player;

    private final Vector3f sceneCenter = new Vector3f(0, 0, 0);
    private final Vector3f lightDir = new Vector3f(-1f).normalize();
    private final Vector3f lightPos = new Vector3f(0);
    private final Vector3f targetLightPos = new Vector3f(sceneCenter).add(new Vector3f(lightDir).scale(-25.0f));
    private final Vector3f lightColor = new Vector3f(1.0f, 0.976f, 0.937f);
    private float lightIntensity = 1f;

    private final Vector3f ambientColor = new Vector3f(0.529411765f, 0.807843137f, 0.921568627f).scale(0.1f);

    private final Vector3f fogColor = new Vector3f(0f);
    private float fogDensity = 0f;

    private boolean sceneDataDirty = false;

    public World(PhysicsContext physicsContext, PlortRenderContext renderContext, PlayerController player) {
        this.allocator = renderContext.allocator();
        this.renderContext = renderContext;
        this.player = player;

        this.physicsContext = physicsContext;

        this.sceneData = new PlortBuffer(SCENE_SIZE, BufferUsage.STORAGE_BUFFER_BIT, allocator);

        uploadSceneData();

        this.aabbBuffer = new BufferedObject<>(PlortBuffer.class, renderContext.swapchain().imageCount(), _ -> null);
    }

    public void shadowManager(ShadowManager manager) {
        this.shadowManager = manager;
    }

    public void uploadSceneData() {
        try (MappedMemory mem = sceneData.map()) {
            mem.putVector3f(lightDir); // light direction
            mem.putVector3f(lightColor); // light color
            mem.putFloat(lightIntensity); // light intensity

            mem.putVector3f(ambientColor); // ambient color

            mem.putVector3f(fogColor); // fog color
            mem.putFloat(fogDensity); // fog density
        }
    }

    public void addEntity(Entity e) {
        this.entities.add(e);
        for (int i = 0; i < renderContext.swapchain().imageCount(); i++) {
            e.setViewBuffer(player.viewBuffer(i), i);
            e.setShadowViewBuffer(shadowManager.sceneShadowViewBuffer(i), i);
        }
    }

    public Vector3f lightPos() {
        return lightPos;
    }

    public Vector3f lightDir() {
        return lightDir;
    }

    public void recreateAABBBuffer(int frameMod) {
        aabbCount = entities.stream().mapToLong(e -> e.bodies().size()).sum() + (DRAW_PLAYER_AABB ? 1 : 0);
        if (aabbCount <= 0) {
            LOGGER.debug("No AABBs to build in buffer.");
            return;
        }
        PlortBuffer buffer = new PlortBuffer(aabbCount * AABB_SIZE, BufferUsage.STORAGE_BUFFER_BIT, allocator);
        try (MappedMemory mem = buffer.map()) {
            Vector3f temp = new Vector3f();
            entities.forEach(e -> e.bodies().forEach(a -> {
                ConstAaBox aabb = a.getWorldSpaceBounds();
                mem.putVector3f(temp.setFrom(aabb.getMin()));
                mem.putFloat(0);
                mem.putVector3f(temp.setFrom(aabb.getMax()));
                mem.putFloat(1);
            }));
            if (DRAW_PLAYER_AABB) {
                mem.putVector3f(temp.setFrom(player.aabb().getMin()));
                mem.putFloat(0);
                mem.putVector3f(temp.setFrom(player.aabb().getMax()));
                mem.putFloat(0);
            }
        }
        aabbBuffer.replace(frameMod, buffer);
    }

    public @Nullable PlortBuffer aabbBuffer(int frameMod) {
        return aabbBuffer.get(frameMod);
    }

    public @NotNull PlortBuffer sceneBuffer() {
        return sceneData;
    }

    public List<Entity> entities() {
        return Collections.unmodifiableList(entities);
    }

    public long aabbCount() {
        return aabbCount;
    }

    public void update(float dt) {
        physicsContext.update(dt, 1); // TODO: fixed timestep
        entities().forEach(e->e.update(dt));

        double time = GLFW.glfwGetTime();

        lightPos.lerp(targetLightPos, 0.0005f, dt);
        lightDir.setFrom(new Vector3f(sceneCenter).subtract(lightPos));

        sceneDataDirty = true;
    }

    public void upload() {
        if (sceneDataDirty) {
            sceneDataDirty = false;
            uploadSceneData();
        }
    }

    @Override
    public void close() {
        try {
            aabbBuffer.close();
        } catch (Exception _) {}
        sceneData.close();
        entities.forEach(Entity::close);
    }
}
