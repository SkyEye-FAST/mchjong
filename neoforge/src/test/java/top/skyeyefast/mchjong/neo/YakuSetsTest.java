package top.skyeyefast.mchjong.neo;

import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import top.skyeyefast.mchjong.engine.*;
import top.skyeyefast.mchjong.config.YakuSets;
import top.skyeyefast.mchjong.network.RiichiRulesPayload;
import top.skyeyefast.mchjong.network.TaiwanRulesPayload;
import top.skyeyefast.mchjong.network.YakuSetsPayload;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class YakuSetsTest {
    @TempDir Path directory;
    private static String definition(int han) {
        return """
            {"variant":"riichi","name":{"en_us":"House","ja_jp":"独自役","zh_cn":"自定义役","zh_tw":"自訂役"},
             "awards":[{"id":"house:five_gates","name":{"en_us":"Five Gates","ja_jp":"五門斉","zh_cn":"五门齐","zh_tw":"五門齊"},
             "description":{"en_us":"All suits, winds and dragons.","ja_jp":"三色、風牌、三元牌を含む。","zh_cn":"包含三种数牌、风牌和三元牌。","zh_tw":"包含三種數牌、風牌和三元牌。"},
             "when":{"type":"categories","values":["man","pin","sou","wind","dragon"]},
             "award":{"type":"han","closed":%d,"open":1}}]}
            """.formatted(han);
    }
    private void reload(Path path) {
        var pack = new PathPackResources(new PackLocationInfo("test", Component.literal("Test"), PackSource.DEFAULT, Optional.empty()), path);
        try (var manager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(pack))) {
            new YakuSets().onResourceManagerReload(manager);
        }
    }

    @Test void dataPackReloadIsAtomicAndDoesNotRewriteSavedRules(MinecraftServer server) throws Exception {
        Path file = directory.resolve("data/house/mchjong/yaku_sets/nested/test.json");
        Files.createDirectories(file.getParent()); Files.writeString(file, definition(2));
        try {
            reload(directory);
            var original = YakuSets.find("house:nested/test", MahjongVariant.RIICHI);
            assertNotNull(original);
            assertNull(YakuSets.find("house:nested/test", MahjongVariant.TAIWAN));
            var rules = RiichiPreset.TENHOU_4.config().withYakuSet(original);
            var proposal = new RiichiRulesPayload(BlockPos.ZERO, UUID.randomUUID(), 1, rules);
            Files.writeString(file, definition(3)); reload(directory);
            var replacement = YakuSets.find("house:nested/test", MahjongVariant.RIICHI);
            assertEquals(3, replacement.awards().getFirst().reward().closed());
            assertEquals(2, rules.yakuSet().awards().getFirst().reward().closed());
            assertNotEquals(proposal.yakuSetDigest(), YakuSetCodec.digest(replacement));
            Files.writeString(file, definition(3).replace("\"closed\":3", "\"closed\":999"));
            assertThrows(IllegalArgumentException.class, () -> reload(directory));
            assertEquals(replacement, YakuSets.find("house:nested/test", MahjongVariant.RIICHI));
            Files.delete(file); reload(directory);
            assertNull(YakuSets.find("house:nested/test", MahjongVariant.RIICHI));
            assertEquals(original, rules.yakuSet());
        } finally {
            try (var manager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of())) { new YakuSets().onResourceManagerReload(manager); }
        }
    }

    @Test void catalogAndSelectionRoundTripWithoutClientDefinedRules(MinecraftServer server) {
        var set = YakuSetCodec.parse("house:test", definition(2));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess(), ConnectionType.NEOFORGE);
        try {
            var catalog = new YakuSetsPayload(List.of(set));
            YakuSetsPayload.CODEC.encode(buffer, catalog);
            assertEquals(catalog, YakuSetsPayload.CODEC.decode(buffer));
            buffer.clear();
            var selection = new RiichiRulesPayload(BlockPos.ZERO, UUID.randomUUID(), 7, RiichiPreset.TENHOU_4.config().withYakuSet(set));
            RiichiRulesPayload.CODEC.encode(buffer, selection);
            assertTrue(buffer.readableBytes() < 1024, "Selections carry an ID and digest, not award documents");
            assertEquals(selection, RiichiRulesPayload.CODEC.decode(buffer));
            var taiwan = YakuSetCodec.parse("house:taiwan", definition(2).replace("\"riichi\"", "\"taiwan\"")
                .replace("\"han\",\"closed\":2,\"open\":1", "\"tai\",\"value\":3"));
            var taiwanRules = TaiwanGameState.Rules.of(TaiwanPreset.POCKET_COMMON.rules()).withYakuSet(taiwan);
            var taiwanSelection = new TaiwanRulesPayload(BlockPos.ZERO, UUID.randomUUID(), UUID.randomUUID(), 7, taiwanRules);
            assertTrue(taiwanSelection.baseRules().yakuSet().isEmpty());
            assertEquals(taiwanRules, taiwanSelection.rules(taiwan));
            buffer.clear();
            TaiwanRulesPayload.CODEC.encode(buffer, taiwanSelection);
            assertEquals(taiwanSelection, TaiwanRulesPayload.CODEC.decode(buffer));
            assertThrows(IllegalArgumentException.class, () -> new TaiwanRulesPayload(BlockPos.ZERO, UUID.randomUUID(), UUID.randomUUID(),
                7, taiwanRules, taiwan.id(), YakuSetCodec.digest(taiwan)));
            assertThrows(IllegalArgumentException.class, () -> new YakuSetsPayload(List.of(set, set)));
            buffer.clear(); buffer.writeVarInt(YakuSetsPayload.MAX_SETS + 1);
            assertThrows(IllegalArgumentException.class, () -> YakuSetsPayload.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
}
