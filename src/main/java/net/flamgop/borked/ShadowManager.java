package net.flamgop.borked;

import net.flamgop.borked.camera.PlayerController;
import net.flamgop.borked.camera.ViewHelper;
import net.flamgop.borked.math.Matrix4f;
import net.flamgop.borked.math.Vector3f;
import net.flamgop.borked.renderer.PlortRenderContext;
import net.flamgop.borked.renderer.memory.BufferUsage;
import net.flamgop.borked.renderer.memory.BufferedObject;
import net.flamgop.borked.renderer.memory.PlortBuffer;

public class ShadowManager implements AutoCloseable {

    private final World world;
    private final PlayerController controller;

    private final BufferedObject<PlortBuffer> sceneShadowViewBuffers;
    private final Matrix4f sceneShadowView = new Matrix4f();
    private final Matrix4f sceneShadowProjection = new Matrix4f();
    private final Vector3f sceneShadowLightPos = new Vector3f();

    private final BufferedObject<PlortBuffer> playerShadowViewBuffers;
    private final Matrix4f playerShadowView = new Matrix4f();
    private final Matrix4f playerShadowProjection = new Matrix4f();
    private final Vector3f playerShadowLightPos = new Vector3f(), playerShadowCurrentLightPos = new Vector3f();
    private final float playerShadowLightSmoothSpeed = 0.0005f;

    public ShadowManager(PlortRenderContext context, World world, PlayerController controller) {
        this.world = world;
        this.controller = controller;
        this.sceneShadowViewBuffers = new BufferedObject<>(PlortBuffer.class, context.swapchain().imageCount(), _ -> new PlortBuffer(PlayerController.VIEW_SIZE, BufferUsage.UNIFORM_BUFFER_BIT, context.allocator()));
        this.playerShadowViewBuffers = new BufferedObject<>(PlortBuffer.class, context.swapchain().imageCount(), _ -> new PlortBuffer(PlayerController.VIEW_SIZE, BufferUsage.UNIFORM_BUFFER_BIT, context.allocator()));
    }

    private void updateSceneShadowValues() {
        Vector3f pos = controller.cameraPosition();
        sceneShadowLightPos.setFrom(world.lightPos()).add(pos);
        sceneShadowView.identity().lookAt(sceneShadowLightPos, pos, new Vector3f(0,1,0));

        float orthoHalfSize = 25.0f;
        sceneShadowProjection.identity().orthographic(
                -orthoHalfSize, orthoHalfSize,
                -orthoHalfSize, orthoHalfSize,
                0.01f, 50f,
                true
        );
    }

    private void updatePlayerShadowValues() {
        Vector3f pos = controller.cameraPosition();
        playerShadowLightPos.setFrom(playerShadowCurrentLightPos).add(pos);
        playerShadowView.identity().lookAt(playerShadowLightPos, pos, new Vector3f(0,1,0));

        float orthoHalfSize = 25.0f;
        playerShadowProjection.identity().orthographic(
                -orthoHalfSize, orthoHalfSize,
                -orthoHalfSize, orthoHalfSize,
                0.01f, 50f,
                true
        );
    }

    public void update(float dt) {
        playerShadowCurrentLightPos.lerp(controller.targetLightPos(), playerShadowLightSmoothSpeed, dt);
        updateSceneShadowValues();
        updatePlayerShadowValues();
    }

    public void upload(int imageIndex) {
        ViewHelper.uploadViewBuffer(sceneShadowViewBuffers.get(imageIndex), sceneShadowView, sceneShadowProjection, sceneShadowLightPos);
        ViewHelper.uploadViewBuffer(playerShadowViewBuffers.get(imageIndex), playerShadowView, playerShadowProjection, playerShadowLightPos);
    }

    public PlortBuffer sceneShadowViewBuffer(int imageIndex) {
        return sceneShadowViewBuffers.get(imageIndex);
    }

    public PlortBuffer playerShadowViewBuffer(int imageIndex) {
        return playerShadowViewBuffers.get(imageIndex);
    }

    @Override
    public void close() {
        try {
            sceneShadowViewBuffers.close();
            playerShadowViewBuffers.close();
        } catch (Exception _) {}
    }
}
