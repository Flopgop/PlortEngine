package net.flamgop.plort.engine.renderer.descriptor;

public sealed interface DescriptorWrite permits BufferDescriptorWrite, TextureDescriptorWrite {
    int count();
    PlortDescriptor.Type type();
    int dstBinding();
    long dstSet();
    boolean valid();
}
