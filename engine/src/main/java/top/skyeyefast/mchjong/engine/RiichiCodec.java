package top.skyeyefast.mchjong.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** The private Riichi state boundary. Only detached records cross Gson. */
final class RiichiCodec {
    private static final Gson JSON = new GsonBuilder().disableJdkUnsafe().serializeNulls().create();

    private RiichiCodec() {}

    static JsonElement save(RiichiSession session) { return JSON.toJsonTree(session.save()); }

    static RiichiSession restore(JsonElement state) {
        try {
            requireFields(state, RiichiSession.State.class);
            return RiichiSession.restore(JSON.fromJson(state, RiichiSession.State.class));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid Riichi session save", invalid);
        }
    }

    private static void requireFields(JsonElement value, Type type) {
        if (value.isJsonNull()) return;
        if (type instanceof ParameterizedType parameterized) {
            if (parameterized.getRawType() == java.util.List.class) {
                for (var item : value.getAsJsonArray()) requireFields(item, parameterized.getActualTypeArguments()[0]);
            } else if (parameterized.getRawType() == Map.class) {
                var object = value.getAsJsonObject();
                for (var entry : object.entrySet()) {
                    Type key = parameterized.getActualTypeArguments()[0];
                    if (key == UUID.class && !UUID.fromString(entry.getKey()).toString().equalsIgnoreCase(entry.getKey()))
                        throw new JsonParseException("Invalid Riichi map key");
                    requireFields(entry.getValue(), parameterized.getActualTypeArguments()[1]);
                }
            }
        } else if (type instanceof Class<?> record && record.isRecord()) {
            JsonObject object = value.getAsJsonObject();
            var components = record.getRecordComponents();
            Set<String> names = Arrays.stream(components).map(java.lang.reflect.RecordComponent::getName).collect(Collectors.toSet());
            if (!object.keySet().equals(names)) throw new JsonParseException("Unexpected fields in " + record.getSimpleName());
            for (var component : components) {
                var field = object.get(component.getName());
                if (component.getType().isPrimitive() && field.isJsonNull())
                    throw new JsonParseException("Null Riichi primitive: " + component.getName());
                requireFields(field, component.getGenericType());
            }
        } else if (type == int.class || type == Integer.class || type == long.class || type == Long.class) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                throw new JsonParseException("Expected a Riichi integer");
            if (type == long.class || type == Long.class) value.getAsBigDecimal().longValueExact();
            else value.getAsBigDecimal().intValueExact();
        } else if (type == double.class || type == Double.class) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !Double.isFinite(value.getAsDouble())) throw new JsonParseException("Expected a finite Riichi number");
        } else if (type == boolean.class || type == Boolean.class) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
                throw new JsonParseException("Expected a Riichi boolean");
        } else if (type == UUID.class) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || !UUID.fromString(value.getAsString()).toString().equalsIgnoreCase(value.getAsString()))
                throw new JsonParseException("Expected a canonical Riichi UUID");
        } else if (type == String.class || type instanceof Class<?> entry && entry.isEnum()) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                throw new JsonParseException("Expected a Riichi string");
        }
    }
}
