package net.flamgop.borked.renderer.descriptor;

public sealed interface DescriptorWrite permits BufferDescriptorWrite, TextureDescriptorWrite {
    int count();
    PlortDescriptor.Type type();
    int dstBinding();
    long dstSet();
    boolean valid();
}
