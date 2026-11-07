package com.psgitech.library.util;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Shared Gson instance provider with LocalDate serialization and deserialization support.
 */
public class GsonProvider {
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, (JsonSerializer<LocalDate>) (date, type, context) ->
                    date != null ? new JsonPrimitive(date.format(DateTimeFormatter.ISO_LOCAL_DATE)) : null)
            .registerTypeAdapter(LocalDate.class, (JsonDeserializer<LocalDate>) (json, type, context) ->
                    json != null && !json.getAsString().isEmpty() ? LocalDate.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE) : null)
            .serializeNulls()
            .create();

    public static Gson getGson() {
        return GSON;
    }
}

