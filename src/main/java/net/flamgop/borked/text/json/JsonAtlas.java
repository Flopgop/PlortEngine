package net.flamgop.borked.text.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record JsonAtlas(String type, int distanceRange, int distanceRangeMiddle, double size, int width, int height, String yOrigin) {
    public static Codec<JsonAtlas> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
                Codec.STRING.fieldOf("type").forGetter(JsonAtlas::type),
                Codec.INT.fieldOf("distanceRange").forGetter(JsonAtlas::distanceRange),
                Codec.INT.fieldOf("distanceRangeMiddle").forGetter(JsonAtlas::distanceRangeMiddle),
                Codec.DOUBLE.fieldOf("size").forGetter(JsonAtlas::size),
                Codec.INT.fieldOf("width").forGetter(JsonAtlas::width),
                Codec.INT.fieldOf("height").forGetter(JsonAtlas::height),
                Codec.STRING.fieldOf("yOrigin").forGetter(JsonAtlas::yOrigin)
        ).apply(instance, JsonAtlas::new)
    );
}
