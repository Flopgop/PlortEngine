package net.flamgop.borked.model;

import net.flamgop.borked.renderer.material.PlortTexture;

public record PlortMaterial(String id, PlortTexture albedo, PlortTexture normal) {
    public static final int TEXTURE_COUNT = 2;
}
