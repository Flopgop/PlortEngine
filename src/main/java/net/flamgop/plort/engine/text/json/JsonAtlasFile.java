package net.flamgop.plort.engine.text.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.flamgop.plort.engine.util.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public record JsonAtlasFile(JsonAtlas atlas, JsonMetrics metrics, List<JsonGlyph> glyphs /*, List<Kerning> kerningPairs*/) {
    public static final Codec<JsonAtlasFile> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    JsonAtlas.CODEC.fieldOf("atlas").forGetter(JsonAtlasFile::atlas),
                    JsonMetrics.CODEC.fieldOf("metrics").forGetter(JsonAtlasFile::metrics),
                    Codec.list(JsonGlyph.CODEC).fieldOf("glyphs").forGetter(JsonAtlasFile::glyphs)
            ).apply(instance, JsonAtlasFile::new)
    );

    public static JsonAtlasFile loadFromResources(String path) throws IOException {
        try (InputStream stream = JsonAtlasFile.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Failed to open resource \"" + path + "\"");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return CODEC.parse(JsonOps.INSTANCE, JsonUtil.parse(reader)).getOrThrow();
            }
        }
    }
}
