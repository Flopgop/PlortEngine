package net.flamgop.borked;

import net.flamgop.borked.renderer.descriptor.*;
import net.flamgop.borked.renderer.PlortRenderContext;
import net.flamgop.borked.renderer.model.PlortModel;
import net.flamgop.borked.renderer.renderpass.*;
import net.flamgop.borked.renderer.image.*;
import net.flamgop.borked.renderer.memory.*;
import net.flamgop.borked.renderer.pipeline.*;
import net.flamgop.borked.renderer.util.VkUtil;
import net.flamgop.borked.renderer.window.CursorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Game {

    private static final Logger LOGGER = LoggerFactory.getLogger(Game.class);

    private final PlortRenderContext renderContext;
    private final CameraController cameraController;
    private final World world;
    private final Renderer renderer;

    public Game() {
        LOGGER.debug("This is a debug string");
        LOGGER.info("This is an info string");
        LOGGER.warn("This is a warning string");
        LOGGER.error("This is an error string");

        renderContext = new PlortRenderContext("Game", VkUtil.makeApiVersion(1,0,0,0));

        this.cameraController = new CameraController(renderContext.allocator(), renderContext.window(), 90, 0.1f);
        this.world = new World(renderContext.allocator(), cameraController);

        this.renderer = new Renderer(renderContext, cameraController, world);

        world.entities.add(new Entity(new PlortModel(renderContext, "1_coffeeShop_post.glb"), renderContext.allocator()));
        world.recreateAABBBuffer();
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

            float fdt = (float) deltaTime;
            cameraController.update(world, fdt);
            world.update(fdt);
        }
        cleanup();
    }

    public void cleanup() {
        renderer.waitIdle();

        world.close();
        cameraController.close();

        renderer.close();

        renderContext.close();
    }
}
