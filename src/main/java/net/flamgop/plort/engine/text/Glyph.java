package net.flamgop.plort.engine.text;

import net.flamgop.plort.engine.math.val.Vector2d;
import net.flamgop.plort.engine.math.val.Vector4d;

public value record Glyph(Vector4d uv, Vector2d size, Vector2d bearing, int atlasIndex, double advance, boolean isEmpty) {
}
