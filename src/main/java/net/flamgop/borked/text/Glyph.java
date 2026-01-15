package net.flamgop.borked.text;

import net.flamgop.borked.math.Vector2d;
import net.flamgop.borked.math.Vector4d;

public record Glyph(Vector4d uv, Vector2d size, Vector2d bearing, int atlasIndex, double advance, boolean isEmpty) {
}
