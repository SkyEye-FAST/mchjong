package top.skyeyefast.mchjong.neo;

import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import top.skyeyefast.mchjong.engine.*;
import top.skyeyefast.mchjong.item.*;
import top.skyeyefast.mchjong.network.*;
import top.skyeyefast.mchjong.world.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class TaiwanIntegrationTest {
    @Test void separateCasesRequireCompleteUniformNativeStockForEachPreset() {
        var complete = MahjongSupplies.stockedBox(RedFives.NONE); var original = complete.copy();
        var contents = MahjongSupplies.contents(complete);
        for (int i = 0; i < MahjongSupplies.TILE_SLOTS; i++) if (!contents.get(i).isEmpty() && MahjongSupplies.tile(contents.get(i)).face() >= 34) contents.set(i, ItemStack.EMPTY);
        var ordinary = complete.copy(); MahjongSupplies.setContents(ordinary, contents);
        var equipment = new TableEquipment(() -> {}); equipment.boxes().setItem(0, ordinary);
        assertEquals(136, equipment.taiwanStock(TaiwanPreset.SOUTHERN_COMMON.rules()).tiles().size());
        assertNull(equipment.taiwanStock(TaiwanPreset.POCKET_COMMON.rules()));
        var flowers = MahjongSupplies.contents(complete);
        for (int i = 0; i < MahjongSupplies.TILE_SLOTS; i++) if (!flowers.get(i).isEmpty() && MahjongSupplies.tile(flowers.get(i)).face() < 34) flowers.set(i, ItemStack.EMPTY);
        var flowerBox = complete.copy(); MahjongSupplies.setContents(flowerBox, flowers); equipment.boxes().setItem(1, flowerBox);
        assertNull(equipment.taiwanStock(TaiwanPreset.POCKET_COMMON.rules()), "Cases cannot pool their tiles");
        equipment.boxes().setItem(1, complete);
        assertEquals(144, equipment.taiwanStock(TaiwanPreset.POCKET_COMMON.rules()).tiles().size());
        assertTrue(ItemStack.matches(original, complete));
        assertNull(TaiwanDeck.select(MahjongSupplies.stockedBox(RedFives.THREE), false));
        var mismatched = MahjongSupplies.contents(complete); mismatched.get(0).set(DataComponents.BASE_COLOR, DyeColor.RED);
        var different = complete.copy(); MahjongSupplies.setContents(different, mismatched);
        assertNull(TaiwanDeck.select(different, true)); assertNull(TaiwanDeck.select(different, false));
        var deck = TaiwanDeck.select(complete, true);
        for (var flower : FlowerTile.values()) assertEquals(34 + flower.ordinal(), deck.tile(flower.id()).face());
    }

    @Test void registeredPayloadsAndPrivateNbtRoundTripWithoutPublishingHands(MinecraftServer server) {
        UUID id = UUID.randomUUID(), incarnation = UUID.randomUUID();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess(), ConnectionType.NEOFORGE);
        try {
            var action = new TaiwanActionPayload(BlockPos.ZERO, id, incarnation, 9, 2);
            TaiwanActionPayload.CODEC.encode(buffer, action); assertEquals(action, TaiwanActionPayload.CODEC.decode(buffer));
            var confirm = new TaiwanNextHandPayload(BlockPos.ZERO, id, incarnation, 9);
            TaiwanNextHandPayload.CODEC.encode(buffer, confirm); assertEquals(confirm, TaiwanNextHandPayload.CODEC.decode(buffer));
            var clock = new TaiwanClockPayload(BlockPos.ZERO, id, incarnation, 9, new TimeControl(120, 30));
            TaiwanClockPayload.CODEC.encode(buffer, clock); assertEquals(clock, TaiwanClockPayload.CODEC.decode(buffer));
            for (var preset : TaiwanPreset.values()) {
                var rules = new TaiwanRulesPayload(BlockPos.ZERO, id, incarnation, 9, TaiwanGameState.Rules.of(preset.rules()));
                TaiwanRulesPayload.CODEC.encode(buffer, rules); assertEquals(rules, TaiwanRulesPayload.CODEC.decode(buffer));
                var roster = java.util.stream.IntStream.range(0, 4).mapToObj(s -> new TableParticipant(UUID.randomUUID(), "Player " + s)).toList();
                var deck = TaiwanDeck.select(MahjongSupplies.stockedBox(RedFives.NONE), preset.rules().getFlowers() != TaiwanRules.Flowers.NONE);
                var session = TaiwanSession.start(id, roster, 4, deck.tiles(), preset.rules());
                var spectator = session.view(null);
                var payload = new TaiwanViewPayload(BlockPos.ZERO, TaiwanCodec.encodeSessionView(spectator), session.roomView(null), deck,
                    DyeColor.CYAN, true, false, false, session.roomSettings(), WorldSettings.Policy.DEFAULT);
                TaiwanViewPayload.CODEC.encode(buffer, payload); assertEquals(payload, TaiwanViewPayload.CODEC.decode(buffer));
                assertTrue(spectator.game().seats().stream().allMatch(s -> s.concealed().isEmpty()));
                assertFalse(payload.view().contains("seed"));
                var source = new MahjongTableBlockEntity(BlockPos.ZERO, MahjongContent.AUTO_TABLE.defaultBlockState());
                source.equipment().boxes().setItem(0, MahjongSupplies.stockedBox(RedFives.NONE)); source.equipment().installCloth(new ItemStack(MahjongContent.CLOTH_ITEM));
                var nbt = source.saveWithoutMetadata(server.registryAccess());
                nbt.putByteArray("session", TableSessionCodec.save(session).getBytes(StandardCharsets.UTF_8));
                var table = new MahjongTableBlockEntity(BlockPos.ZERO, MahjongContent.AUTO_TABLE.defaultBlockState()); table.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, server.registryAccess(), nbt));
                var restored = (TaiwanSession) TableSessionCodec.restore(new String(table.saveWithoutMetadata(server.registryAccess()).getByteArray("session").orElseThrow(), StandardCharsets.UTF_8));
                assertEquals(MahjongVariant.TAIWAN, restored.variant()); assertNotEquals(session.incarnation(), restored.incarnation());
                assertEquals(session.scores(), restored.scores()); assertEquals(session.view(null).game().seats(), restored.view(null).game().seats());
                var appearance = table.getUpdateTag(server.registryAccess()); assertFalse(appearance.contains("session"));
                assertEquals(deck.tiles().size(), table.equipment().taiwanStock(preset.rules()).tiles().size());
            }
        } finally { buffer.release(); }
    }
}
