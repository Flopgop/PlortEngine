package net.flamgop.borked.renderer.text;

import org.joml.Vector2d;
import org.joml.Vector4d;

public record Glyph(Vector4d uv, Vector2d size, Vector2d bearing, double advance, boolean isEmpty) {
}
