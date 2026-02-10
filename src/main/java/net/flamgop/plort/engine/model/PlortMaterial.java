package net.flamgop.plort.engine.model;

import net.flamgop.plort.engine.renderer.material.PlortTexture;

public record PlortMaterial(String id, PlortTexture albedo, PlortTexture normal) {
    public static final int TEXTURE_COUNT = 2;
}
