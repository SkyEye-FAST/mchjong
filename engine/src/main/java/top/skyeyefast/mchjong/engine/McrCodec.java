package top.skyeyefast.mchjong.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Bounded MCR JSON boundaries. Gson and the private live game never cross the public API. */
public final class McrCodec {
    private static final int MAX_CHARS = 65_536;
    private static final Gson JSON = new GsonBuilder().disableJdkUnsafe().serializeNulls()
        .registerTypeAdapter(McrSettlement.Result.class, new ResultAdapter()).create();

    private McrCodec() {}

    public static String save(McrGame game) { return encode(game.save()); }

    public static String saveSession(McrSession session) { return encode(session.save()); }

    public static McrSession restoreSession(String json) {
        try {
            return McrSession.restore(decode(json, McrSession.State.class));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid MCR session save", invalid);
        }
    }

    public static String encodeSessionView(McrSession.View view) { return encode(java.util.Objects.requireNonNull(view)); }

    public static McrSession.View decodeSessionView(String json) {
        try {
            return decode(json, McrSession.View.class);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid MCR session view", invalid);
        }
    }

    /** Accepts an already-redacted view, never live state or a private save. */
    public static String encodeView(McrView view) { return encode(java.util.Objects.requireNonNull(view)); }

    public static McrView decodeView(String json) {
        try {
            return decode(json, McrView.class);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid MCR view", invalid);
        }
    }

    /** Restore atomically; malformed saves throw and never silently start a replacement match. */
    public static McrGame restore(String json) {
        try {
            return McrGame.restore(decode(json, McrGameState.class));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid MCR save", invalid);
        }
    }

    private static String encode(Object value) {
        String json = JSON.toJson(value);
        if (json.length() > MAX_CHARS) throw new IllegalArgumentException("MCR data exceeds its size limit");
        return json;
    }

    private static <T> T decode(String json, Class<T> type) {
        if (json == null || json.length() > MAX_CHARS) throw new IllegalArgumentException("Invalid MCR data size");
        validateDocument(json);
        try (var reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            JsonElement tree = JSON.getAdapter(JsonElement.class).read(reader);
            if (tree == null || tree.isJsonNull() || reader.peek() != JsonToken.END_DOCUMENT)
                throw new JsonParseException("Expected exactly one MCR object");
            requireFields(tree, type);
            return JSON.fromJson(tree, type);
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid MCR JSON", error);
        }
    }

    /** Bound nesting and reject duplicate keys before materializing any JSON tree. */
    private static void validateDocument(String json) {
        try (var reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) throw new JsonParseException("Expected an MCR object");
            var names = new ArrayDeque<Set<String>>();
            while (reader.peek() != JsonToken.END_DOCUMENT) {
                switch (reader.peek()) {
                    case BEGIN_OBJECT, BEGIN_ARRAY -> {
                        if (names.size() >= 16) throw new JsonParseException("MCR data is nested too deeply");
                        if (reader.peek() == JsonToken.BEGIN_OBJECT) reader.beginObject();
                        else reader.beginArray();
                        names.push(new HashSet<>());
                    }
                    case END_OBJECT, END_ARRAY -> {
                        if (reader.peek() == JsonToken.END_OBJECT) reader.endObject();
                        else reader.endArray();
                        names.pop();
                    }
                    case NAME -> {
                        if (!names.element().add(reader.nextName())) throw new JsonParseException("Duplicate MCR field");
                    }
                    case STRING, NUMBER -> reader.nextString();
                    case BOOLEAN -> reader.nextBoolean();
                    case NULL -> reader.nextNull();
                    default -> throw new JsonParseException("Unexpected MCR JSON token");
                }
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid MCR JSON", error);
        }
    }

    // Gson defaults missing primitive record fields to zero/false. Saves must be complete,
    // including explicit nulls, so a missing seed or stop-win field cannot change resumed play.
    private static void requireFields(JsonElement value, Type type) {
        if (value.isJsonNull()) return;
        if (type instanceof ParameterizedType list && list.getRawType() == java.util.List.class) {
            for (var item : value.getAsJsonArray()) requireFields(item, list.getActualTypeArguments()[0]);
        } else if (type instanceof Class<?> record && record.isRecord()) {
            JsonObject object = value.getAsJsonObject();
            var components = record.getRecordComponents();
            var names = Arrays.stream(components).map(java.lang.reflect.RecordComponent::getName).collect(Collectors.toSet());
            if (!object.keySet().equals(names)) throw new JsonParseException("Unexpected fields in " + record.getSimpleName());
            for (var component : components) {
                var field = object.get(component.getName());
                if (component.getType().isPrimitive() && field.isJsonNull())
                    throw new JsonParseException("Null primitive: " + component.getName());
                requireFields(field, component.getGenericType());
            }
        } else if (type == int.class || type == Integer.class || type == long.class) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                throw new JsonParseException("Expected an MCR integer");
            try {
                if (type == long.class) value.getAsBigDecimal().longValueExact();
                else value.getAsBigDecimal().intValueExact();
            } catch (ArithmeticException error) {
                throw new JsonParseException("MCR integer is out of range", error);
            }
        } else if (type == boolean.class) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
                throw new JsonParseException("Expected an MCR boolean");
        } else if (type == java.util.UUID.class) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || !java.util.UUID.fromString(value.getAsString()).toString().equalsIgnoreCase(value.getAsString()))
                throw new JsonParseException("Expected a canonical MCR UUID");
        } else if (type == String.class || type instanceof Class<?> entry && entry.isEnum()) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                throw new JsonParseException("Expected an MCR string");
        }
    }

    private static final class ResultAdapter implements JsonSerializer<McrSettlement.Result>, JsonDeserializer<McrSettlement.Result> {
        @Override public JsonElement serialize(McrSettlement.Result result, Type type, JsonSerializationContext context) {
            var object = new JsonObject();
            if (result instanceof McrSettlement.Win win) {
                object.addProperty("type", "win");
                object.add("win", context.serialize(win));
            } else object.addProperty("type", "draw");
            return object;
        }

        @Override public McrSettlement.Result deserialize(JsonElement value, Type type, JsonDeserializationContext context) {
            var object = value.getAsJsonObject();
            String kind = object.get("type").getAsString();
            if (kind.equals("draw") && object.keySet().equals(Set.of("type"))) return new McrSettlement.Draw();
            if (!kind.equals("win") || !object.keySet().equals(Set.of("type", "win")) || object.get("win").isJsonNull())
                throw new JsonParseException("Invalid MCR result tag");
            requireFields(object.get("win"), McrSettlement.Win.class);
            return context.deserialize(object.get("win"), McrSettlement.Win.class);
        }
    }
}
