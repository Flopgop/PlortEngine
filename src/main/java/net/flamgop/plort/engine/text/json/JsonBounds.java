package net.flamgop.plort.engine.text.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record JsonBounds(double left, double bottom, double right, double top) {
    public static final Codec<JsonBounds> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.DOUBLE.fieldOf("left").forGetter(JsonBounds::left),
                    Codec.DOUBLE.fieldOf("bottom").forGetter(JsonBounds::bottom),
                    Codec.DOUBLE.fieldOf("right").forGetter(JsonBounds::right),
                    Codec.DOUBLE.fieldOf("top").forGetter(JsonBounds::top)
            ).apply(instance, JsonBounds::new)
    );
}
