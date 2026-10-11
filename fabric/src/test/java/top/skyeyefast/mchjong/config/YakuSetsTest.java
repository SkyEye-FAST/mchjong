package top.skyeyefast.mchjong.config;

import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.skyeyefast.mchjong.engine.*;
import top.skyeyefast.mchjong.network.RiichiRulesPayload;
import top.skyeyefast.mchjong.network.TaiwanRulesPayload;
import top.skyeyefast.mchjong.network.YakuSetsPayload;
import static org.junit.jupiter.api.Assertions.*;

class YakuSetsTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrapMinecraft() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
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
        var pack = new PathPackResources("test", path, false);
        try (var manager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(pack))) {
            new YakuSets().onResourceManagerReload(manager);
        }
    }

    @Test void dataPackReloadIsAtomicAndDoesNotRewriteSavedRules() throws Exception {
        Path file = directory.resolve("data/house/mchjong/yaku_sets/nested/test.json");
        Files.createDirectories(file.getParent()); Files.writeString(file, definition(2));
        try {
            reload(directory);
            var original = YakuSets.find("house:nested/test", MahjongVariant.RIICHI);
            assertNotNull(original);
            assertNull(YakuSets.find("house:nested/test", MahjongVariant.TAIWAN));
            var rules = RiichiPreset.TENHOU_4.config().withYakuSelection(YakuSelection.of(original));
            var proposal = new RiichiRulesPayload(BlockPos.ZERO, UUID.randomUUID(), 1, rules);
            Files.writeString(file, definition(3)); reload(directory);
            var replacement = YakuSets.find("house:nested/test", MahjongVariant.RIICHI);
            assertEquals(3, replacement.awards().getFirst().reward().closed());
            assertEquals(2, rules.yakuSelection().find("house:five_gates").reward().closed());
            assertNotEquals(proposal.selection().sets().get(original.id()).digest(), YakuSetCodec.digest(replacement));
            assertNull(proposal.selection().resolve(MahjongVariant.RIICHI, id -> YakuSets.find(id, MahjongVariant.RIICHI)));
            Files.writeString(file, definition(3).replace("\"closed\":3", "\"closed\":999"));
            assertThrows(IllegalArgumentException.class, () -> reload(directory));
            assertEquals(replacement, YakuSets.find("house:nested/test", MahjongVariant.RIICHI));
            Files.delete(file); reload(directory);
            assertNull(YakuSets.find("house:nested/test", MahjongVariant.RIICHI));
            assertEquals(YakuSelection.of(original), rules.yakuSelection());
        } finally {
            try (var manager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of())) { new YakuSets().onResourceManagerReload(manager); }
        }
    }

    @Test void catalogAndSelectionRoundTripWithoutClientDefinedRules() {
        var set = YakuSetCodec.parse("house:test", definition(2));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var catalog = new YakuSetsPayload(List.of(set));
            catalog.write(buffer);
            assertEquals(catalog, YakuSetsPayload.decode(buffer));
            buffer.clear();
            var selection = new RiichiRulesPayload(BlockPos.ZERO, UUID.randomUUID(), 7, RiichiPreset.TENHOU_4.config().withYakuSelection(YakuSelection.of(set)));
            selection.write(buffer);
            assertTrue(buffer.readableBytes() < 1024, "Selections carry an ID and digest, not award documents");
            assertEquals(selection, RiichiRulesPayload.decode(buffer));
            var taiwan = YakuSetCodec.parse("house:taiwan", definition(2).replace("\"riichi\"", "\"taiwan\"")
                .replace("\"han\",\"closed\":2,\"open\":1", "\"tai\",\"value\":3"));
            var taiwanRules = TaiwanGameState.Rules.of(TaiwanPreset.POCKET_COMMON.rules()).withYakuSelection(YakuSelection.of(taiwan));
            var taiwanSelection = new TaiwanRulesPayload(BlockPos.ZERO, UUID.randomUUID(), UUID.randomUUID(), 7, taiwanRules);
            assertTrue(taiwanSelection.baseRules().yakuSelection().isDefault());
            assertEquals(taiwanRules, taiwanSelection.rules(YakuSelection.of(taiwan)));
            buffer.clear();
            taiwanSelection.write(buffer);
            assertEquals(taiwanSelection, TaiwanRulesPayload.decode(buffer));
            assertThrows(IllegalArgumentException.class, () -> new TaiwanRulesPayload(BlockPos.ZERO, UUID.randomUUID(), UUID.randomUUID(),
                7, taiwanRules, top.skyeyefast.mchjong.network.YakuSelectionRequest.of(YakuSelection.of(taiwan))));
            var mixed = YakuSelection.of(set).withSet(BuiltinYakuSets.MAHJONG_SOUL_ANCIENT, true).withNative(YakuCatalog.awardId("Tanyao"), false);
            var request = top.skyeyefast.mchjong.network.YakuSelectionRequest.of(mixed);
            assertEquals(mixed, request.resolve(MahjongVariant.RIICHI, id -> id.equals(set.id()) ? set : BuiltinYakuSets.MAHJONG_SOUL_ANCIENT));
            assertNull(new top.skyeyefast.mchjong.network.YakuSelectionRequest(java.util.Map.of(set.id(), new top.skyeyefast.mchjong.network.YakuSelectionRequest.Choice(YakuSetCodec.digest(set), 2)), request.disabledNative())
                .resolve(MahjongVariant.RIICHI, id -> set));
            assertThrows(IllegalArgumentException.class, () -> new YakuSetsPayload(List.of(set, set)));
            buffer.clear(); buffer.writeVarInt(YakuSetsPayload.MAX_SETS + 1);
            assertThrows(IllegalArgumentException.class, () -> YakuSetsPayload.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test void fullCatalogSelectionFitsProposalAndActiveTaiwanViewPackets() {
        var text = new YakuSet.Text("界".repeat(56), "界".repeat(56), "界".repeat(56), "界".repeat(56));
        var condition = new YakuCondition(YakuCondition.Type.CLOSED, List.of(), List.of("true"), 0, 0);
        var sets = new java.util.ArrayList<YakuSet>();
        var enabled = new java.util.HashSet<String>();
        for (int i = 0; i < YakuSelection.MAX_SETS; i++) {
            var awards = new java.util.ArrayList<YakuSet.Definition>();
            for (int j = 0; j < 3; j++) {
                String id = "bulk:award_" + i + "_" + j; enabled.add(id);
                awards.add(new YakuSet.Definition(id, text, text, condition, new YakuSet.Reward(YakuSet.RewardType.TAI, 1, 1), List.of()));
            }
            sets.add(new YakuSet("bulk:set_" + i, MahjongVariant.TAIWAN, text, awards));
        }
        var catalog = new YakuSetsPayload(sets);
        var selected = new YakuSelection(MahjongVariant.TAIWAN, sets, java.util.Set.of(), enabled);
        var rules = TaiwanGameState.Rules.of(TaiwanPreset.POCKET_COMMON.rules()).withYakuSelection(selected);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            catalog.write(buffer);
            assertTrue(buffer.readableBytes() > 150000);
            assertEquals(catalog, YakuSetsPayload.decode(buffer)); buffer.clear();
            var proposal = new TaiwanRulesPayload(BlockPos.ZERO, UUID.randomUUID(), UUID.randomUUID(), 1, rules);
            proposal.write(buffer);
            assertTrue(buffer.readableBytes() < 32767);
            assertEquals(proposal, TaiwanRulesPayload.decode(buffer)); buffer.clear();
            var players = java.util.stream.IntStream.range(0, 4).mapToObj(i -> new TableParticipant(new UUID(44, i), "Player " + i,
                false, false, BotDifficulty.EASY, null, true)).toList();
            var session = TaiwanSession.start(UUID.randomUUID(), players, 55, Tile.standard144Set(), rules.restore());
            var view = session.view(players.getFirst().id());
            String json = TaiwanCodec.encodeSessionView(view);
            assertEquals(view, TaiwanCodec.decodeSessionView(json));
            buffer.writeUtf(json, 524288);
            buffer.writeUtf(TaiwanCodec.encodeRules(rules), TaiwanCodec.MAX_RULES_CHARS);
            assertTrue(buffer.readableBytes() + 50000 < 1048576, "Active view repeats rules; leave room for room/world/appearance fields");
        } finally { buffer.release(); }
    }
}
