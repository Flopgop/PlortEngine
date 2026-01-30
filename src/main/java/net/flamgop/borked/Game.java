package net.flamgop.borked;

import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.JsonOps;
import net.flamgop.borked.camera.PlayerController;
import net.flamgop.borked.entity.ComponentStore;
import net.flamgop.borked.entity.EntityManager;
import net.flamgop.borked.entity.components.PhysicsBody;
import net.flamgop.borked.entity.components.RenderInstance;
import net.flamgop.borked.entity.components.Renderable;
import net.flamgop.borked.entity.components.Transform;
import net.flamgop.borked.entity.system.EntityPhysicsSystem;
import net.flamgop.borked.entity.system.EntityRenderSystem;
import net.flamgop.borked.renderer.PlortRenderContext;
import net.flamgop.borked.model.PlortModel;
import net.flamgop.borked.renderer.util.VkUtil;
import net.flamgop.borked.renderer.window.CursorState;
import net.flamgop.borked.resource.ResourceIdentifier;
import net.flamgop.borked.resource.ResourceManager;
import net.flamgop.borked.util.ECSUtil;
import net.flamgop.borked.util.JsonUtil;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.Optional;

public class Game {

    private static @Nullable Game INSTANCE = null;

    public static Optional<Game> instance() {
        return Optional.ofNullable(INSTANCE);
    }

    private static final Path SAVE_PATH = Path.of("./save.json");
    private static final Logger LOGGER = LoggerFactory.getLogger(Game.class);

    private final ResourceManager resourceManager;

    private final PlortRenderContext renderContext;
    private final PlayerController playerController;
    private final EntityManager entityManager;
    private final EntityRenderSystem renderSystem;
    private final EntityPhysicsSystem physicsSystem;
    private final ShadowManager shadowManager;
    private final Renderer renderer;

    private final PlortModel cube;
    private final PlortModel scene;

    private final GameState state;

    public Game() {
        INSTANCE = this;
        LOGGER.debug("This is a debug string");
        LOGGER.info("This is an info string");
        LOGGER.warn("This is a warning string");
        LOGGER.error("This is an error string");
        try {
            this.resourceManager = new ResourceManager(Path.of("assets"));
            try (InputStream stream = this.resourceManager.open(new ResourceIdentifier("borked", "test.txt"))) {
                String str = new String(stream.readAllBytes());
                LOGGER.info("Loaded resource: {}", str);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.state = this.loadState();
        this.renderContext = new PlortRenderContext("Game", VkUtil.makeApiVersion(1,0,0,0));

        this.entityManager = new EntityManager();
        this.physicsSystem = new EntityPhysicsSystem();

        this.playerController = new PlayerController(resourceManager, physicsSystem.context(), renderContext, renderContext.window(), 90, 0.1f);
        this.shadowManager = new ShadowManager(renderContext, playerController);

        cube = new PlortModel(renderContext, resourceManager, new ResourceIdentifier("borked", "model/cube.glb"));

        this.renderer = new Renderer(resourceManager, state, renderContext, playerController, entityManager, shadowManager);
        this.renderSystem = new EntityRenderSystem(resourceManager, renderContext, playerController, renderer.gbuffer().renderPass(), renderer.shadowRenderPass());
        renderer.sceneData(renderSystem.sceneData());
        shadowManager.sceneData(renderSystem.sceneData());

        scene = new PlortModel(renderContext, resourceManager, new ResourceIdentifier("borked", "model/test_scene.glb"));
        int entityId = entityManager.createEntity();
        ComponentStore<Transform> transformStore = entityManager.store(Transform.class);
        ComponentStore<Renderable> renderableStore = entityManager.store(Renderable.class);
        ComponentStore<RenderInstance> renderInstanceStore = entityManager.store(RenderInstance.class);
        ComponentStore<PhysicsBody> physicsBodyStore = entityManager.store(PhysicsBody.class);

        Transform transform = new Transform();
        transform.transform().translate(playerController.playerPosition().add(0,5,0));
        RenderInstance renderInstance = new RenderInstance(renderContext.device(), renderContext.allocator(), scene, renderContext.swapchain().imageCount());
        PhysicsBody physicsBody = new PhysicsBody(ECSUtil.createBodiesFromModel(physicsSystem.context(), scene, transform));
        physicsBody.bodies().forEach(b -> b.setUserData(entityId));

        for (int i = 0; i < renderContext.swapchain().imageCount(); i++) {
            final int frame = i;
            scene.writeViewBuffer(playerController.viewBuffer(frame), n -> renderInstance.descriptorSetPool().descriptorSet(frame, n));
            scene.writeViewBuffer(shadowManager.sceneShadowViewBuffer(frame), n -> renderInstance.shadowDescriptorSetPool().descriptorSet(frame, n));
        }

        scene.writeDescriptors(renderContext, renderInstance.descriptorSetPool());
        scene.writeDescriptors(renderContext, renderInstance.shadowDescriptorSetPool());

        transformStore.add(entityId, transform);
        renderableStore.add(entityId, new Renderable(scene));
        renderInstanceStore.add(entityId, renderInstance);
        physicsBodyStore.add(entityId, physicsBody);
    }

    private GameState loadState() {
        File stateFile = Game.SAVE_PATH.toFile();
        if (!stateFile.exists())
            return new GameState();

        try (BufferedReader reader = new BufferedReader(new FileReader(stateFile))) {
            return GameState.CODEC.parse(JsonOps.INSTANCE, JsonUtil.parse(reader)).getOrThrow();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void start() {
        long prevFrameStart = System.nanoTime();
        double deltaTime = 0;

        renderContext.window().input().setCursorState(CursorState.DISABLED);

        while (renderer.windowOpen()) {
            renderContext.window().input().update();
            renderContext.window().pollEvents();

            int imageIndex = renderer.startFrame();
            if (imageIndex == -1) continue;

            float fdt = (float) deltaTime;
            playerController.update(renderSystem.sceneData(), physicsSystem.context(), fdt);
            physicsSystem.update(entityManager, fdt);
            shadowManager.update(fdt);

            renderSystem.recreateAABBBuffer(entityManager, imageIndex);

            playerController.upload(imageIndex);
            shadowManager.upload(imageIndex);

            if (!renderer.frame(renderSystem, imageIndex, deltaTime)) continue;

            long frameEnd = System.nanoTime();
            deltaTime = (frameEnd - prevFrameStart) / 1e+9;
            prevFrameStart = System.nanoTime();

            if (renderContext.window().input().keyPressed(GLFW.GLFW_KEY_R)) {
                state.renderDebug(!state.renderDebug());
            }

            if (renderContext.window().input().keyPressed(GLFW.GLFW_KEY_Q)) {
                int entityId = entityManager.createEntity();
                ComponentStore<Transform> transformStore = entityManager.store(Transform.class);
                ComponentStore<Renderable> renderableStore = entityManager.store(Renderable.class);
                ComponentStore<RenderInstance> renderInstanceStore = entityManager.store(RenderInstance.class);
                ComponentStore<PhysicsBody> physicsBodyStore = entityManager.store(PhysicsBody.class);

                Transform transform = new Transform();
                transform.transform().translate(playerController.playerPosition().add(0,5,0));
                RenderInstance renderInstance = new RenderInstance(renderContext.device(), renderContext.allocator(), cube, renderContext.swapchain().imageCount());
                PhysicsBody physicsBody = new PhysicsBody(ECSUtil.createBodiesFromModel(physicsSystem.context(), scene, transform));
                physicsBody.bodies().forEach(b -> b.setUserData(entityId));

                for (int i = 0; i < renderContext.swapchain().imageCount(); i++) {
                    final int frame = i;
                    cube.writeViewBuffer(playerController.viewBuffer(frame), n -> renderInstance.descriptorSetPool().descriptorSet(frame, n));
                    cube.writeViewBuffer(shadowManager.sceneShadowViewBuffer(frame), n -> renderInstance.shadowDescriptorSetPool().descriptorSet(frame, n));
                }

                cube.writeDescriptors(renderContext, renderInstance.descriptorSetPool());
                cube.writeDescriptors(renderContext, renderInstance.shadowDescriptorSetPool());

                transformStore.add(entityId, transform);
                renderableStore.add(entityId, new Renderable(cube));
                renderInstanceStore.add(entityId, renderInstance);
                physicsBodyStore.add(entityId, physicsBody);
            }
        }
        cleanup();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void saveState(GameState state) {
        GameState.CODEC.encodeStart(JsonOps.INSTANCE, state).ifSuccess(e -> {
            try {
                File outputFile = Game.SAVE_PATH.toFile();
                if (!outputFile.exists()) {
                    File parent = outputFile.getParentFile();
                    parent.mkdirs();
                    outputFile.createNewFile();
                }
                try (JsonWriter writer = new JsonWriter(new BufferedWriter(new FileWriter(outputFile)))) {
                    Streams.write(e, writer);
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    public void cleanup() {
        renderer.waitIdle();

        Game.saveState(state);

        ComponentStore<RenderInstance> renderInstanceStore = entityManager.store(RenderInstance.class);
        for (RenderInstance instance : renderInstanceStore.components()) instance.close();

        shadowManager.close();
        playerController.close();
        cube.close();
        scene.close();

        renderSystem.close();
        renderer.close();

        renderContext.close();
        INSTANCE = null;
    }
}
