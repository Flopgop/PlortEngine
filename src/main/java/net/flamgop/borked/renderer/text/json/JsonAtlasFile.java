package net.flamgop.borked.renderer.text.json;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class JsonAtlasFile {
    public JsonAtlas atlas;
    public JsonMetrics metrics;
    public ArrayList<JsonGlyph> glyphs;
    public ArrayList<Object> kerning;

    public static JsonAtlasFile loadFromResources(String path) {
        try (InputStream stream = JsonAtlasFile.class.getClassLoader().getResourceAsStream(path)) {
            String json = new String(stream.readAllBytes());
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, JsonAtlasFile.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}