package top.skyeyefast.mchjong.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Human-authored data-pack syntax is normalized into a complete typed snapshot. */
public final class YakuSetCodec {
    public static final int MAX_CHARS = 32768;
    private static final Gson JSON = new GsonBuilder().disableJdkUnsafe().serializeNulls().create();
    private YakuSetCodec() {}

    public static String encode(YakuSet value) {
        String json = JSON.toJson(value);
        if (json.length() > MAX_CHARS) throw new IllegalArgumentException("Award set exceeds document limit");
        return json;
    }
    public static YakuSet decode(String json) { return SichuanCodec.decode(json, YakuSet.class, MAX_CHARS); }
    /** Binds a selection to the definition the player reviewed, including its localized descriptions. */
    public static String digest(YakuSet value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(encode(value).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException unavailable) { throw new AssertionError(unavailable); }
    }

    public static YakuSet parse(String id, String json) {
        validate(json);
        var root = JsonParser.parseString(json).getAsJsonObject();
        fields(root, Set.of("variant", "name", "awards"), Set.of());
        var variant = MahjongVariant.valueOf(string(root.get("variant")).toUpperCase(Locale.ROOT));
        var awards = new ArrayList<YakuSet.Definition>();
        for (var entry : root.getAsJsonArray("awards")) {
            var a = entry.getAsJsonObject();
            fields(a, Set.of("id", "name", "description", "when", "award"), Set.of("excludes"));
            var reward = a.getAsJsonObject("award");
            String type = string(reward.get("type"));
            var unit = YakuSet.RewardType.valueOf(type.toUpperCase(Locale.ROOT));
            if (unit == YakuSet.RewardType.TAI) fields(reward, Set.of("type", "value"), Set.of());
            else fields(reward, Set.of("type", "closed", "open"), Set.of());
            int closed = integer(reward.get(unit == YakuSet.RewardType.TAI ? "value" : "closed"));
            int open = unit == YakuSet.RewardType.TAI ? closed : integer(reward.get("open"));
            awards.add(new YakuSet.Definition(string(a.get("id")), text(a.get("name")), text(a.get("description")),
                condition(a.getAsJsonObject("when")), new YakuSet.Reward(unit, closed, open),
                a.has("excludes") ? strings(a.get("excludes")) : List.of()));
        }
        var value = new YakuSet(id, variant, text(root.get("name")), awards);
        encode(value);
        return value;
    }

    private static YakuCondition condition(JsonObject object) {
        for (String logic : List.of("all", "any", "not")) if (object.has(logic)) {
            fields(object, Set.of(logic), Set.of());
            var children = new ArrayList<YakuCondition>();
            if (logic.equals("not")) children.add(condition(object.getAsJsonObject(logic)));
            else for (var child : object.getAsJsonArray(logic)) children.add(condition(child.getAsJsonObject()));
            return new YakuCondition(YakuCondition.Type.valueOf(logic.toUpperCase(Locale.ROOT)), children, List.of(), 0, 0);
        }
        fields(object, Set.of("type"), Set.of("values", "min", "max"));
        var type = YakuCondition.Type.valueOf(string(object.get("type")).toUpperCase(Locale.ROOT));
        boolean count = switch (type) { case TILES, GROUPS, REDS, FLOWERS, SAME_SEQUENCES -> true; default -> false; };
        if (count && (!object.has("min") || !object.has("max")))
            throw new IllegalArgumentException("Count conditions require min and max");
        return new YakuCondition(type, List.of(), object.has("values") ? strings(object.get("values")) : List.of(),
            object.has("min") ? integer(object.get("min")) : 0, object.has("max") ? integer(object.get("max")) : 0);
    }
    private static YakuSet.Text text(JsonElement element) {
        var object = element.getAsJsonObject();
        fields(object, Set.of("en_us", "ja_jp", "zh_cn", "zh_tw"), Set.of());
        return new YakuSet.Text(string(object.get("en_us")), string(object.get("ja_jp")), string(object.get("zh_cn")), string(object.get("zh_tw")));
    }
    private static List<String> strings(JsonElement element) {
        var result = new ArrayList<String>();
        for (var item : element.getAsJsonArray()) result.add(string(item));
        return List.copyOf(result);
    }
    private static String string(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected award string");
        return value.getAsString();
    }
    private static int integer(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Expected award integer");
        return value.getAsBigDecimal().intValueExact();
    }
    private static void fields(JsonObject object, Set<String> required, Set<String> optional) {
        if (!object.keySet().containsAll(required) || object.keySet().stream().anyMatch(k -> !required.contains(k) && !optional.contains(k)))
            throw new IllegalArgumentException("Unexpected award fields: " + object.keySet());
    }
    private static void validate(String json) {
        if (json == null || json.length() > MAX_CHARS) throw new IllegalArgumentException("Award document exceeds limit");
        try (var reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) throw new IllegalArgumentException("Expected award object");
            var names = new ArrayDeque<Set<String>>();
            while (reader.peek() != JsonToken.END_DOCUMENT) switch (reader.peek()) {
                case BEGIN_OBJECT, BEGIN_ARRAY -> {
                    if (names.size() >= 16) throw new IllegalArgumentException("Award document is too deep");
                    if (reader.peek() == JsonToken.BEGIN_OBJECT) reader.beginObject(); else reader.beginArray();
                    names.push(new HashSet<>());
                }
                case END_OBJECT, END_ARRAY -> {
                    if (reader.peek() == JsonToken.END_OBJECT) reader.endObject(); else reader.endArray();
                    names.pop();
                }
                case NAME -> { if (!names.element().add(reader.nextName())) throw new IllegalArgumentException("Duplicate award field"); }
                case STRING, NUMBER -> reader.nextString();
                case BOOLEAN -> reader.nextBoolean();
                case NULL -> reader.nextNull();
                default -> throw new IllegalArgumentException("Unexpected award token");
            }
        } catch (java.io.IOException | IllegalStateException error) {
            throw new IllegalArgumentException("Invalid award JSON", error);
        }
    }
}
