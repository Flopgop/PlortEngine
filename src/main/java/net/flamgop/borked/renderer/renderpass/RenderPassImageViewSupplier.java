package net.flamgop.borked.renderer.renderpass;

public interface RenderPassImageViewSupplier {
    long consume(int width, int height, int frameInFlight);
}
