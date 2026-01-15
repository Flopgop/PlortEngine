package net.flamgop.borked;

import net.flamgop.borked.camera.PlayerController;
import net.flamgop.borked.math.Vector3f;
import net.flamgop.borked.physics.next.PhysicsContext;
import net.flamgop.borked.renderer.PlortRenderContext;
import net.flamgop.borked.model.PlortModel;
import net.flamgop.borked.renderer.util.VkUtil;
import net.flamgop.borked.renderer.window.CursorState;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Game {

    private static final Logger LOGGER = LoggerFactory.getLogger(Game.class);

    private final PlortRenderContext renderContext;
    private final PlayerController playerController;
    private final World world;
    private final Renderer renderer;
    private final PhysicsContext physics;

    private final PlortModel cube;
    private final PlortModel scene;

    public Game() {
        LOGGER.debug("This is a debug string");
        LOGGER.info("This is an info string");
        LOGGER.warn("This is a warning string");
        LOGGER.error("This is an error string");

        this.physics = new PhysicsContext();
        this.renderContext = new PlortRenderContext("Game", VkUtil.makeApiVersion(1,0,0,0));

        this.playerController = new PlayerController(physics, renderContext, renderContext.window(), 90, 0.1f);
        this.world = new World(physics, renderContext, playerController);

        cube = new PlortModel(renderContext, "cube.glb");

        this.renderer = new Renderer(renderContext, playerController, world);

        scene = new PlortModel(renderContext, "test_scene.glb");
        world.addEntity(new Entity(physics, scene, renderContext.allocator()));
        for (int i = 0; i < renderContext.swapchain().imageCount(); i++) {
            world.recreateAABBBuffer(i);
        }
    }

    public void start() {
        long prevFrameStart = System.nanoTime();
        double deltaTime = 0;

        renderContext.window().input().setCursorState(CursorState.DISABLED);

        while (renderer.windowOpen()) {
            renderContext.window().input().update();
            renderContext.window().pollEvents();

            if (!renderer.frame(deltaTime)) continue;

            long frameEnd = System.nanoTime();
            deltaTime = (frameEnd - prevFrameStart) / 1e+9;
            prevFrameStart = System.nanoTime();

            if (renderContext.window().input().keyPressed(GLFW.GLFW_KEY_R)) {
                GameState.renderDebug = !GameState.renderDebug;
            }

            if (renderContext.window().input().keyPressed(GLFW.GLFW_KEY_Q)) {
                Entity e = new Entity(physics, cube, renderContext.allocator());
                e.setPosition(new Vector3f(playerController.position().add(0, 5, 0)));
                world.addEntity(e);
            }

            float fdt = (float) deltaTime;
            playerController.update(world, physics, fdt);
            world.update(fdt);
            world.upload(renderer.currentFrameModInFlight());
            world.recreateAABBBuffer(renderer.currentFrameModInFlight());
        }
        cleanup();
    }

    public void cleanup() {
        renderer.waitIdle();

        world.close();
        playerController.close();
        cube.close();
        scene.close();

        renderer.close();

        renderContext.close();
    }
}
