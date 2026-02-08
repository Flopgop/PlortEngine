package net.flamgop.borked.text;

import net.flamgop.borked.math.val.Vector2d;
import net.flamgop.borked.math.val.Vector4d;

public value record Glyph(Vector4d uv, Vector2d size, Vector2d bearing, int atlasIndex, double advance, boolean isEmpty) {
}
