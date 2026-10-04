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
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** One private save envelope containing exactly one concrete session and its current match. */
public final class TableSessionCodec {
    private static final int FORMAT = 2;
    private static final int MAX_CHARS = 8 * 1024 * 1024;
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();

    private TableSessionCodec() {}

    public static String save(TableSession session) {
        if (session.variant() == null) throw new IllegalArgumentException("Unregistered runtimes use their independent codec");
        var envelope = new JsonObject();
        envelope.addProperty("format", FORMAT);
        envelope.addProperty("variant", session.variant().name());
        envelope.add("state", switch (session.variant()) {
            case RIICHI -> RiichiCodec.save((RiichiSession) session);
            case MCR -> parse(McrCodec.saveSession((McrSession) session));
            case SICHUAN -> parse(SichuanCodec.saveSession((SichuanSession) session));
        });
        String encoded = JSON.toJson(envelope);
        if (encoded.length() > MAX_CHARS) throw new IllegalArgumentException("Table save exceeds its size limit");
        return encoded;
    }

    public static TableSession restore(String encoded) {
        var envelope = parse(encoded).getAsJsonObject();
        if (!envelope.keySet().equals(Set.of("format", "variant", "state"))
            || !envelope.get("format").toString().equals(Integer.toString(FORMAT))
            || !envelope.get("state").isJsonObject())
            throw new IllegalArgumentException("Unsupported table session save");
        MahjongVariant variant = MahjongVariant.valueOf(envelope.get("variant").getAsString());
        TableSession session = switch (variant) {
            case RIICHI -> RiichiCodec.restore(envelope.get("state"));
            case MCR -> McrCodec.restoreSession(envelope.get("state").toString());
            case SICHUAN -> SichuanCodec.restoreSession(envelope.get("state").toString());
        };
        if (session.variant() != variant) throw new IllegalArgumentException("Table variant does not match its state");
        return session;
    }

    private static JsonElement parse(String encoded) {
        if (encoded == null || encoded.isEmpty() || encoded.length() > MAX_CHARS)
            throw new IllegalArgumentException("Invalid table save size");
        try (var reader = new JsonReader(new StringReader(encoded))) {
            reader.setLenient(false);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) throw new JsonParseException("Expected a table save object");
            var names = new ArrayDeque<Set<String>>();
            while (reader.peek() != JsonToken.END_DOCUMENT) {
                switch (reader.peek()) {
                    case BEGIN_OBJECT, BEGIN_ARRAY -> {
                        if (names.size() >= 32) throw new JsonParseException("Table save nested too deeply");
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
                        if (!names.element().add(reader.nextName())) throw new JsonParseException("Duplicate table save field");
                    }
                    case STRING, NUMBER -> reader.nextString();
                    case BOOLEAN -> reader.nextBoolean();
                    case NULL -> reader.nextNull();
                    default -> throw new JsonParseException("Unexpected table save token");
                }
            }
            return JSON.fromJson(encoded, JsonObject.class);
        } catch (IOException | JsonParseException invalid) {
            throw new IllegalArgumentException("Invalid table session save", invalid);
        }
    }
}
