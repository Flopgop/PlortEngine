package net.flamgop.borked.text.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record JsonMetrics(int emSize, double lineHeight, double ascender, double descender, double underlineY, double underlineThickness) {
    public static final Codec<JsonMetrics> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("emSize").forGetter(JsonMetrics::emSize),
                    Codec.DOUBLE.fieldOf("lineHeight").forGetter(JsonMetrics::lineHeight),
                    Codec.DOUBLE.fieldOf("ascender").forGetter(JsonMetrics::ascender),
                    Codec.DOUBLE.fieldOf("descender").forGetter(JsonMetrics::ascender),
                    Codec.DOUBLE.fieldOf("underlineY").forGetter(JsonMetrics::ascender),
                    Codec.DOUBLE.fieldOf("underlineThickness").forGetter(JsonMetrics::ascender)
            ).apply(instance, JsonMetrics::new)
    );
}
