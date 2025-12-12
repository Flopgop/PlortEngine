package net.flamgop.borked.renderer.descriptor;

import net.flamgop.borked.renderer.pipeline.PlortDescriptor;

public sealed interface DescriptorWrite permits BufferDescriptorWrite, TextureDescriptorWrite {
    int count();
    PlortDescriptor.Type type();
    int dstBinding();
    long dstSet();
    boolean valid();
}
