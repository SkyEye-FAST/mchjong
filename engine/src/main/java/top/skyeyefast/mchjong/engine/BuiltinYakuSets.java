package top.skyeyefast.mchjong.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Bundled examples use the same declarative schema as server data packs. */
public final class BuiltinYakuSets {
    public static final String MAHJONG_SOUL_ANCIENT_ID = "mahjongsoul:ancient";
    public static final YakuSet MAHJONG_SOUL_ANCIENT = load();
    private BuiltinYakuSets() {}

    private static YakuSet load() {
        try (var input = BuiltinYakuSets.class.getResourceAsStream("/yaku_sets/mahjong_soul_ancient.json")) {
            if (input == null) throw new IllegalStateException("Missing bundled ancient awards");
            return YakuSetCodec.parse(MAHJONG_SOUL_ANCIENT_ID, new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException error) { throw new ExceptionInInitializerError(error); }
    }
}
