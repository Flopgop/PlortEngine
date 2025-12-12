package net.flamgop.borked.renderer.text;

import org.joml.Vector2f;
import org.joml.Vector3f;

public record Text(String text, Vector3f color, Vector2f offset, float scale, TextAlign align, float depth) {
    public Text(String text) {
        this(text, new Vector3f(1f), new Vector2f(0f), 1f, TextAlign.LEFT, 0.1f);
    }

    public Text(String text, Vector3f color) {
        this(text, color, new Vector2f(0f), 1f, TextAlign.LEFT, 0.1f);
    }

    public Text(String text, Vector3f color, Vector2f offset) {
        this(text, color, offset, 1f, TextAlign.LEFT, 0.1f);
    }

    public Text(String text, Vector3f color, Vector2f offset, float scale) {
        this(text, color, offset, scale, TextAlign.LEFT, 0.1f);
    }

    public Text(String text, Vector3f color, Vector2f offset, float scale, TextAlign align) {
        this(text, color, offset, scale, align, 0.1f);
    }
}
