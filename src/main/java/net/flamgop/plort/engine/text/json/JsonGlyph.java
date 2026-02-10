package net.flamgop.plort.engine.text.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

public record JsonGlyph(int unicode, double advance, Optional<JsonBounds> planeBounds, Optional<JsonBounds> atlasBounds) {
    public static final Codec<JsonGlyph> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("unicode").forGetter(JsonGlyph::unicode),
                    Codec.DOUBLE.fieldOf("advance").forGetter(JsonGlyph::advance),
                    JsonBounds.CODEC.optionalFieldOf("planeBounds").forGetter(JsonGlyph::planeBounds),
                    JsonBounds.CODEC.optionalFieldOf("atlasBounds").forGetter(JsonGlyph::atlasBounds)
            ).apply(instance, JsonGlyph::new)
    );
}
