package top.skyeyefast.mchjong.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
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

/** Bounded Taiwan JSON boundaries. Gson and the private live game never cross the public API. */
public final class TaiwanCodec {
    private static final int MAX_CHARS = 65_536;
    private static final int MAX_SESSION_CHARS = 8 * 1024 * 1024;
    private static final Gson JSON = new GsonBuilder().disableJdkUnsafe().serializeNulls().create();

    private TaiwanCodec() {}

    public static String save(TaiwanGame game) { return encode(game.save()); }

    public static String saveSession(TaiwanSession session) { return encode(session.save(), MAX_SESSION_CHARS); }

    public static TaiwanSession restoreSession(String json) {
        try {
            return TaiwanSession.restore(decode(json, TaiwanSession.State.class, MAX_SESSION_CHARS));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid Taiwan session save", invalid);
        }
    }

    public static String encodeSessionView(TaiwanSession.View view) { return encode(java.util.Objects.requireNonNull(view)); }

    public static TaiwanSession.View decodeSessionView(String json) {
        try {
            return decode(json, TaiwanSession.View.class);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid Taiwan session view", invalid);
        }
    }

    /** Accepts an already-redacted view, never live state or a private save. */
    public static String encodeView(TaiwanView view) { return encode(java.util.Objects.requireNonNull(view)); }

    public static TaiwanView decodeView(String json) {
        try {
            return decode(json, TaiwanView.class);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid Taiwan view", invalid);
        }
    }

    /** Restore atomically; malformed saves throw and never silently start a replacement match. */
    public static TaiwanGame restore(String json) {
        try {
            return TaiwanGame.restore(decode(json, TaiwanGameState.class));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid Taiwan save", invalid);
        }
    }

    private static String encode(Object value) { return encode(value, MAX_CHARS); }

    private static String encode(Object value, int limit) {
        String json = JSON.toJson(value);
        if (json.length() > limit) throw new IllegalArgumentException("Taiwan data exceeds its size limit");
        return json;
    }

    private static <T> T decode(String json, Class<T> type) { return decode(json, type, MAX_CHARS); }

    private static <T> T decode(String json, Class<T> type, int limit) {
        if (json == null || json.length() > limit) throw new IllegalArgumentException("Invalid Taiwan data size");
        validateDocument(json);
        try (var reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            JsonElement tree = JSON.getAdapter(JsonElement.class).read(reader);
            if (tree == null || tree.isJsonNull() || reader.peek() != JsonToken.END_DOCUMENT)
                throw new JsonParseException("Expected exactly one Taiwan object");
            requireFields(tree, type);
            return JSON.fromJson(tree, type);
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid Taiwan JSON", error);
        }
    }

    /** Bound nesting and reject duplicate keys before materializing any JSON tree. */
    private static void validateDocument(String json) {
        try (var reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) throw new JsonParseException("Expected an Taiwan object");
            var names = new ArrayDeque<Set<String>>();
            while (reader.peek() != JsonToken.END_DOCUMENT) {
                switch (reader.peek()) {
                    case BEGIN_OBJECT, BEGIN_ARRAY -> {
                        if (names.size() >= 16) throw new JsonParseException("Taiwan data is nested too deeply");
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
                        if (!names.element().add(reader.nextName())) throw new JsonParseException("Duplicate Taiwan field");
                    }
                    case STRING, NUMBER -> reader.nextString();
                    case BOOLEAN -> reader.nextBoolean();
                    case NULL -> reader.nextNull();
                    default -> throw new JsonParseException("Unexpected Taiwan JSON token");
                }
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid Taiwan JSON", error);
        }
    }

    // Gson defaults missing primitive record fields to zero/false. Saves must be complete,
    // including explicit nulls, so missing chronology or passing fields cannot change resumed play.
    private static void requireFields(JsonElement value, Type type) {
        if (value.isJsonNull()) return;
        if (type instanceof ParameterizedType list && (list.getRawType() == java.util.List.class || list.getRawType() == java.util.Set.class)) {
            if (!value.isJsonArray()) throw new JsonParseException("Expected a collection");
            if (list.getRawType() == java.util.Set.class && new HashSet<>(value.getAsJsonArray().asList()).size() != value.getAsJsonArray().size())
                throw new JsonParseException("Duplicate set member");
            for (var item : value.getAsJsonArray()) requireFields(item, list.getActualTypeArguments()[0]);
        } else if (type instanceof ParameterizedType map && map.getRawType() == java.util.Map.class) {
            if (!value.isJsonObject()) throw new JsonParseException("Expected a map");
            var keyType = (Class<?>) map.getActualTypeArguments()[0];
            var keys = Arrays.stream(keyType.getEnumConstants()).map(Object::toString).collect(Collectors.toSet());
            for (var entry : value.getAsJsonObject().entrySet()) {
                if (!keys.contains(entry.getKey()) || entry.getValue().isJsonNull()) throw new JsonParseException("Invalid rule map entry");
                requireFields(entry.getValue(), map.getActualTypeArguments()[1]);
            }
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
        } else if (type == int.class || type == Integer.class || type == long.class || type == Long.class) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                throw new JsonParseException("Expected an Taiwan integer");
            try {
                if (type == long.class || type == Long.class) value.getAsBigDecimal().longValueExact();
                else value.getAsBigDecimal().intValueExact();
            } catch (ArithmeticException error) {
                throw new JsonParseException("Taiwan integer is out of range", error);
            }
        } else if (type == boolean.class || type == Boolean.class) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
                throw new JsonParseException("Expected an Taiwan boolean");
        } else if (type == java.util.UUID.class) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || !java.util.UUID.fromString(value.getAsString()).toString().equalsIgnoreCase(value.getAsString()))
                throw new JsonParseException("Expected a canonical Taiwan UUID");
        } else if (type == String.class || type instanceof Class<?> entry && entry.isEnum()) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                throw new JsonParseException("Expected an Taiwan string");
        }
    }

}
