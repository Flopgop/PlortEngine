package net.flamgop.borked.renderer.descriptor;

import net.flamgop.borked.renderer.image.PlortImage;
import net.flamgop.borked.renderer.image.PlortSampler;
import net.flamgop.borked.renderer.material.PlortTexture;

import java.util.ArrayList;
import java.util.List;

public final class TextureDescriptorWrite implements DescriptorWrite {

    private final PlortDescriptor.Type type;
    private final int dstBinding;
    private final long dstSet;
    private final int count;
    private final PlortImage.Layout layout;

    private final List<PlortImage> images;
    private final List<PlortSampler> samplers;

    public TextureDescriptorWrite(PlortTexture[] textures, PlortImage.Layout layout, int dstBinding, long dstSet) {
        this.type = PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER;
        this.dstBinding = dstBinding;
        this.dstSet = dstSet;
        this.count = textures.length;
        this.layout = layout;
        images = new ArrayList<>(count);
        samplers = new ArrayList<>(count);

        for (PlortTexture texture : textures) {
            images.add(texture.image());
            samplers.add(texture.sampler());
        }
    }

    public TextureDescriptorWrite(PlortImage[] images, PlortImage.Layout layout, int dstBinding, long dstSet) {
        this.type = layout == PlortImage.Layout.SHADER_READ_ONLY_OPTIMAL ? PlortDescriptor.Type.SAMPLED_IMAGE : PlortDescriptor.Type.STORAGE_IMAGE;
        this.dstBinding = dstBinding;
        this.dstSet = dstSet;
        this.count = images.length;
        this.layout = layout;
        this.images = new ArrayList<>(count);
        this.samplers = null;

        this.images.addAll(List.of(images));
    }

    public TextureDescriptorWrite(PlortSampler[] samplers, PlortImage.Layout layout, int dstBinding, long dstSet) {
        this.type = PlortDescriptor.Type.SAMPLER;
        this.dstBinding = dstBinding;
        this.dstSet = dstSet;
        this.count = samplers.length;
        this.layout = layout;
        this.images = null;
        this.samplers = new ArrayList<>(count);

        this.samplers.addAll(List.of(samplers));
    }

    @Override
    public int count() {
        return count;
    }

    @Override
    public PlortDescriptor.Type type() {
        return type;
    }

    @Override
    public int dstBinding() {
        return dstBinding;
    }

    @Override
    public long dstSet() {
        return dstSet;
    }

    public PlortImage.Layout layout() {
        return layout;
    }

    public List<PlortImage> images() {
        return images != null ? images : List.of();
    }

    public List<PlortSampler> samplers() {
        return samplers != null ? samplers : List.of();
    }

    @Override
    public boolean valid() {
        return  type == PlortDescriptor.Type.SAMPLED_IMAGE          ||
                type == PlortDescriptor.Type.STORAGE_IMAGE          ||
                type == PlortDescriptor.Type.COMBINED_IMAGE_SAMPLER ||
                type == PlortDescriptor.Type.SAMPLER;
    }
}
