package top.skyeyefast.mchjong.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class SichuanCodec {
    private static final int MAX_CHARS = 65_536;
    private static final Gson JSON = new GsonBuilder().disableJdkUnsafe().serializeNulls().create();
    private SichuanCodec() {}
    public static String save(SichuanGame game) { return encode(game.save()); }
    public static SichuanGame restore(String json) { return SichuanGame.restore(decode(json, SichuanGame.State.class)); }
    public static String saveSession(SichuanSession session) { return encode(session.save()); }
    public static SichuanSession restoreSession(String json) { return SichuanSession.restore(decode(json, SichuanSession.State.class)); }
    public static String encodeView(SichuanView view) { return encode(Objects.requireNonNull(view)); }
    public static SichuanView decodeView(String json) { return decode(json, SichuanView.class); }
    public static String encodeSessionView(SichuanSession.View view) { return encode(Objects.requireNonNull(view)); }
    public static SichuanSession.View decodeSessionView(String json) { return decode(json, SichuanSession.View.class); }
    private static String encode(Object value) {
        String json = JSON.toJson(value);
        if (json.length() > MAX_CHARS) throw new IllegalArgumentException("Sichuan document exceeds its size limit");
        return json;
    }
    private static <T> T decode(String json, Class<T> type) {
        if (json == null || json.isEmpty() || json.length() > MAX_CHARS) throw new IllegalArgumentException("Invalid Sichuan document size");
        try {
            validateDocument(json);
            try (var reader = new JsonReader(new StringReader(json))) {
                reader.setLenient(false);
                JsonElement tree = JSON.getAdapter(JsonElement.class).read(reader);
                if (tree == null || !tree.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT)
                    throw new JsonParseException("Expected one Sichuan object");
                requireFields(tree, type);
                return JSON.fromJson(tree, type);
            }
        } catch (IOException | RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid Sichuan document", invalid);
        }
    }
    private static void validateDocument(String json) throws IOException {
        try (var reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) throw new JsonParseException("Expected Sichuan object");
            var names = new ArrayDeque<Set<String>>();
            while (reader.peek() != JsonToken.END_DOCUMENT) {
                switch (reader.peek()) {
                    case BEGIN_OBJECT, BEGIN_ARRAY -> {
                        if (names.size() >= 16) throw new JsonParseException("Sichuan document nested too deeply");
                        if (reader.peek() == JsonToken.BEGIN_OBJECT) reader.beginObject(); else reader.beginArray();
                        names.push(new HashSet<>());
                    }
                    case END_OBJECT, END_ARRAY -> {
                        if (reader.peek() == JsonToken.END_OBJECT) reader.endObject(); else reader.endArray();
                        names.pop();
                    }
                    case NAME -> { if (!names.element().add(reader.nextName())) throw new JsonParseException("Duplicate Sichuan field"); }
                    case STRING, NUMBER -> reader.nextString();
                    case BOOLEAN -> reader.nextBoolean();
                    case NULL -> reader.nextNull();
                    default -> throw new JsonParseException("Unexpected Sichuan token");
                }
            }
        }
    }
    private static void requireFields(JsonElement value, Type type) {
        if (value.isJsonNull()) {
            if (type instanceof Class<?> field && field.isPrimitive()) throw new JsonParseException("Null primitive");
            return;
        }
        if (type instanceof ParameterizedType list && list.getRawType() == List.class) {
            for (var item : value.getAsJsonArray()) {
                if (item.isJsonNull()) throw new JsonParseException("Null Sichuan list item");
                requireFields(item, list.getActualTypeArguments()[0]);
            }
        } else if (type instanceof Class<?> record && record.isRecord()) {
            var object = value.getAsJsonObject();
            var fields = record.getRecordComponents();
            var names = Arrays.stream(fields).map(java.lang.reflect.RecordComponent::getName).collect(Collectors.toSet());
            if (!object.keySet().equals(names)) throw new JsonParseException("Unexpected Sichuan fields");
            for (var field : fields) requireFields(object.get(field.getName()), field.getGenericType());
        } else if (type == int.class || type == Integer.class || type == long.class) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new JsonParseException("Expected integer");
            if (type == long.class) value.getAsBigDecimal().longValueExact(); else value.getAsBigDecimal().intValueExact();
        } else if (type == boolean.class) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new JsonParseException("Expected boolean");
        } else if (type == String.class || type == UUID.class || type instanceof Class<?> field && field.isEnum()) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new JsonParseException("Expected string");
            if (type == UUID.class && !UUID.fromString(value.getAsString()).toString().equalsIgnoreCase(value.getAsString()))
                throw new JsonParseException("Expected canonical UUID");
        }
    }
}
