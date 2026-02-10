package net.flamgop.plort.engine.renderer.renderpass;

public interface RenderPassImageViewSupplier {
    long consume(int width, int height, int frameInFlight);
}
