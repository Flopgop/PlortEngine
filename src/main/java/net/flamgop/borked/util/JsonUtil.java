package net.flamgop.borked.util;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.Reader;

public class JsonUtil {
    private static final Gson GSON = new GsonBuilder().create();

    public static JsonObject parse(Reader reader) {
        return JsonUtil.fromJson(GSON, reader, JsonObject.class);
    }

    public static <T> T fromJson(Gson gson, Reader reader, Class<T> clazz) {
        try {
            JsonReader json = new JsonReader(reader);
            json.setStrictness(Strictness.STRICT);
            T object = gson.getAdapter(clazz).read(json);
            if (object == null) {
                throw new JsonParseException("JSON data was null or empty");
            }
            return object;
        } catch (IOException exception) {
            throw new JsonParseException(exception);
        }
    }
}
