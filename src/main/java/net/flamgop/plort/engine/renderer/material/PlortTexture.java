package net.flamgop.plort.engine.renderer.material;

import net.flamgop.plort.engine.renderer.image.PlortImage;
import net.flamgop.plort.engine.renderer.image.PlortSampler;

public record PlortTexture(PlortImage image, PlortSampler sampler) implements AutoCloseable {
    @Override // note: utility helper, if this object does not manage its own image or sampler, do not use this method
    public void close() {
        image.close();
        sampler.close();
    }
}
