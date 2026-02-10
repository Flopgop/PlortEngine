package net.flamgop.borked;

import net.flamgop.borked.camera.PlayerController;
import net.flamgop.borked.camera.ViewHelper;
import net.flamgop.borked.math.val.Frustum;
import net.flamgop.borked.math.val.Matrix4f;
import net.flamgop.borked.math.val.Vector3f;
import net.flamgop.borked.renderer.PlortRenderContext;
import net.flamgop.borked.renderer.memory.BufferUsage;
import net.flamgop.borked.renderer.memory.BufferedObject;
import net.flamgop.borked.renderer.memory.PlortBuffer;
import net.flamgop.borked.world.SceneData;

public class ShadowManager implements AutoCloseable {

    private SceneData sceneData;
    private final PlayerController controller;

    private final BufferedObject<PlortBuffer> sceneShadowViewBuffers;
    private Matrix4f sceneShadowView;
    private Matrix4f sceneShadowProjection;
    private Vector3f sceneShadowLightPos;
    private Frustum sceneShadowFrustum;

    private final BufferedObject<PlortBuffer> playerShadowViewBuffers;
    private Matrix4f playerShadowView;
    private Matrix4f playerShadowProjection;
    private Vector3f playerShadowLightPos, playerShadowCurrentLightPos = new Vector3f();
    private final float playerShadowLightSmoothSpeed = 0.0005f;

    public ShadowManager(PlortRenderContext context, PlayerController controller) {
        this.controller = controller;
        this.sceneShadowViewBuffers = new BufferedObject<>(PlortBuffer.class, context.swapchain().imageCount(), _ -> new PlortBuffer(PlayerController.VIEW_SIZE, BufferUsage.UNIFORM_BUFFER_BIT, context.allocator()));
        this.playerShadowViewBuffers = new BufferedObject<>(PlortBuffer.class, context.swapchain().imageCount(), _ -> new PlortBuffer(PlayerController.VIEW_SIZE, BufferUsage.UNIFORM_BUFFER_BIT, context.allocator()));
        updatePlayerShadowValues();
    }

    public void sceneData(SceneData sceneData) {
        this.sceneData = sceneData;
        updateSceneShadowValues();
    }

    private void updateSceneShadowValues() {
        Vector3f pos = controller.cameraPosition();
        sceneShadowLightPos = sceneData.lightPos().add(pos);
        sceneShadowView = Matrix4f.lookAt(sceneShadowLightPos, pos, new Vector3f(0,1,0));

        float orthoHalfSize = 25.0f;
        sceneShadowProjection = Matrix4f.orthographic(
                -orthoHalfSize, orthoHalfSize,
                -orthoHalfSize, orthoHalfSize,
                0.01f, 50f,
                true
        );

        sceneShadowFrustum = Frustum.fromViewProjectionMatrix(sceneShadowProjection.multiply(sceneShadowView), true);
    }

    public Frustum sceneShadowFrustum() {
        return sceneShadowFrustum;
    }

    private void updatePlayerShadowValues() {
        Vector3f pos = controller.cameraPosition();
        playerShadowLightPos = playerShadowCurrentLightPos.add(pos);
        playerShadowView = Matrix4f.lookAt(playerShadowLightPos, pos, new Vector3f(0,1,0));

        float orthoHalfSize = 25.0f;
        playerShadowProjection = Matrix4f.orthographic(
                -orthoHalfSize, orthoHalfSize,
                -orthoHalfSize, orthoHalfSize,
                0.01f, 50f,
                true
        );
    }

    public void update(float dt) {
        playerShadowCurrentLightPos = playerShadowCurrentLightPos.lerp(controller.targetLightPos(), playerShadowLightSmoothSpeed, dt);
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
