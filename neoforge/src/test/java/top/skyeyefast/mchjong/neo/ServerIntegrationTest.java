package top.skyeyefast.mchjong.neo;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.network.TableActionPayload;
import top.skyeyefast.mchjong.network.TableControlPayload;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.network.TableViewPayload;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import static org.junit.jupiter.api.Assertions.*;

/** Boots NeoForge and its datapacks; deliberately does not manipulate the provider's world. */
@ExtendWith(EphemeralTestServerProvider.class)
class ServerIntegrationTest {
    @Test void registriesAndRecipesLoadOnDedicatedServer(MinecraftServer server) {
        assertSame(MahjongContent.TABLE, BuiltInRegistries.BLOCK.get(MahjongContent.id("mahjong_table")));
        assertSame(MahjongContent.STOOL, BuiltInRegistries.BLOCK.get(MahjongContent.id("mahjong_stool")));
        assertSame(MahjongContent.TABLE_ITEM, BuiltInRegistries.ITEM.get(MahjongContent.id("mahjong_table")));
        assertSame(MahjongContent.TABLE_ENTITY, BuiltInRegistries.BLOCK_ENTITY_TYPE.get(MahjongContent.id("mahjong_table")));
        assertSame(MahjongContent.SEAT_ENTITY, BuiltInRegistries.ENTITY_TYPE.get(MahjongContent.id("seat")));
        top.skyeyefast.mchjong.world.MahjongSounds.EVENTS.forEach((name, sound) ->
            assertSame(sound, BuiltInRegistries.SOUND_EVENT.get(MahjongContent.id(name))));
        assertTrue(server.getRecipeManager().byKey(MahjongContent.id("mahjong_table")).isPresent());
        assertTrue(server.getRecipeManager().byKey(MahjongContent.id("mahjong_stool")).isPresent());
        var commands = server.getCommands().getDispatcher().getRoot().getChild("mchjong");
        assertNotNull(commands);
        for (String name : java.util.List.of("world", "host", "clock", "invite", "accept", "decline", "replays", "replay"))
            assertNotNull(commands.getChild(name), "Missing command: " + name);
    }

    private static Game startedGame() {
        Game game = new Game(UUID.randomUUID(), RuleSet.MAHJONG_SOUL_4, 1892);
        for (int seat = 0; seat < 4; seat++) {
            UUID id = new UUID(5, seat);
            game.join(id, "Player " + seat, seat);
        }
        var host = new UUID(5, 0);
        TableView lobby = game.view(host);
        int begin = java.util.stream.IntStream.range(0, lobby.actions().size())
            .filter(i -> lobby.actions().get(i).type() == Action.Type.BEGIN_SEATING).findFirst().orElseThrow();
        assertTrue(game.act(host, lobby.decision(), begin));
        for (int seat = 0; seat < 4; seat++) {
            UUID id = new UUID(5, seat);
            assertTrue(game.join(id, "Player " + seat, game.seatOf(id)));
            TableView view = game.view(id);
            int ready = -1;
            for (int i = 0; i < view.actions().size(); i++) if (view.actions().get(i).type() == Action.Type.READY) ready = i;
            assertTrue(game.act(id, view.decision(), ready));
        }
        game.validate();
        return game;
    }

    @Test void privateSavedHandsNeverEnterAChunkUpdate(MinecraftServer server) {
        Game game = startedGame();
        CompoundTag saved = new CompoundTag();
        saved.putString("game", TableNetworking.JSON.toJson(game));
        MahjongTableBlockEntity table = new MahjongTableBlockEntity(BlockPos.ZERO, MahjongContent.AUTO_TABLE.defaultBlockState());
        table.loadWithComponents(saved, server.registryAccess());
        var box = top.skyeyefast.mchjong.item.MahjongSupplies.completeBox(
            top.skyeyefast.mchjong.item.TileMaterial.GLASS, net.minecraft.world.item.DyeColor.PURPLE);
        box = top.skyeyefast.mchjong.item.MahjongSupplies.engrave(box, top.skyeyefast.mchjong.item.TileFacePreset.KANTO);
        table.equipment().boxes().setItem(0, box.copy());
        var cloth = new net.minecraft.world.item.ItemStack(MahjongContent.CLOTH_ITEM);
        cloth.set(net.minecraft.core.component.DataComponents.BASE_COLOR, net.minecraft.world.item.DyeColor.LIME);
        table.equipment().installCloth(cloth);
        CompoundTag restored = table.saveWithoutMetadata(server.registryAccess());
        assertTrue(restored.contains("game"));
        assertTrue(restored.contains("boxes"));
        assertTrue(restored.contains("cloth"));
        Game copy = TableNetworking.JSON.fromJson(restored.getString("game"), Game.class);
        copy.validate();
        assertEquals(TableNetworking.JSON.toJson(game.view(null)), TableNetworking.JSON.toJson(copy.view(null)));
        CompoundTag appearance = table.getUpdateTag(server.registryAccess());
        assertEquals(java.util.Set.of("wood", "color", "cloth_color", "tile_material", "tile_back", "tile_preset"), appearance.getAllKeys());
        assertEquals("glass", appearance.getString("tile_material"));
        assertEquals("mchjong:kanto", appearance.getString("tile_preset"));
        table.loadWithComponents(appearance, server.registryAccess());
        CompoundTag afterPublicUpdate = table.saveWithoutMetadata(server.registryAccess());
        assertEquals(restored.getString("game"), afterPublicUpdate.getString("game"));
        assertTrue(net.minecraft.world.item.ItemStack.matches(box, table.equipment().boxes().getItem(0)));
        // The ephemeral server provides registries but no loaded level. Live packet delivery is
        // exercised by both client smoke runs; here the exact packet-tag whitelist is the contract.
        var loaded = new MahjongTableBlockEntity(BlockPos.ZERO, MahjongContent.AUTO_TABLE.defaultBlockState());
        loaded.loadWithComponents(restored, server.registryAccess());
        assertTrue(net.minecraft.world.item.ItemStack.matches(box, loaded.equipment().boxes().getItem(0)));
        assertEquals(appearance, loaded.getUpdateTag(server.registryAccess()));
        assertEquals(top.skyeyefast.mchjong.item.TileFacePreset.KANTO, loaded.equipment().preset());
        var empty = new MahjongTableBlockEntity(BlockPos.ZERO, MahjongContent.AUTO_TABLE.defaultBlockState());
        loaded.loadWithComponents(empty.saveWithoutMetadata(server.registryAccess()), server.registryAccess());
        assertFalse(loaded.saveWithoutMetadata(server.registryAccess()).contains("game"));
        assertTrue(loaded.equipment().boxes().isEmpty());
    }

    @Test void actualMinecraftCodecsRoundTripOnlyDeclaredPayloads(MinecraftServer server) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess(), ConnectionType.NEOFORGE);
        try {
            var request = new TableActionPayload(new BlockPos(-5, 72, 9), UUID.randomUUID(), 17, 3);
            TableActionPayload.CODEC.encode(buffer, request);
            assertEquals(request, TableActionPayload.CODEC.decode(buffer));
            var seatRequest = new top.skyeyefast.mchjong.network.TableSeatPayload(request.pos(), request.tableId());
            top.skyeyefast.mchjong.network.TableSeatPayload.CODEC.encode(buffer, seatRequest);
            assertEquals(seatRequest, top.skyeyefast.mchjong.network.TableSeatPayload.CODEC.decode(buffer));
            for (var operation : TableControlPayload.Operation.values()) for (boolean enabled : new boolean[]{false, true}) {
                var control = new TableControlPayload(new BlockPos(-6, 70, 20), UUID.randomUUID(), operation, Long.MAX_VALUE, enabled);
                TableControlPayload.CODEC.encode(buffer, control);
                assertEquals(control, TableControlPayload.CODEC.decode(buffer));
            }
            for (boolean maximum : new boolean[]{false, true}) {
                var rules = RuleSet.WRC.config();
                for (var option : top.skyeyefast.mchjong.engine.RuleOption.values())
                    rules = rules.with(option, maximum ? option.max() : option.min());
                var proposal = new top.skyeyefast.mchjong.network.TableRulesPayload(request.pos(), request.tableId(), Long.MAX_VALUE, rules);
                top.skyeyefast.mchjong.network.TableRulesPayload.CODEC.encode(buffer, proposal);
                assertEquals(proposal, top.skyeyefast.mchjong.network.TableRulesPayload.CODEC.decode(buffer));
            }
            Game game = startedGame();
            var payload = new TableViewPayload(BlockPos.ZERO, TableNetworking.JSON.toJson(game.view(null)), false, true, 63, game.roomView());
            TableViewPayload.CODEC.encode(buffer, payload);
            assertEquals(payload, TableViewPayload.CODEC.decode(buffer));
            TableView decoded = TableNetworking.JSON.fromJson(payload.view(), TableView.class);
            assertTrue(decoded.actions().isEmpty());
            assertTrue(decoded.seats().stream().flatMap(seat -> seat.hand().stream()).allMatch(tile -> tile == -1));
            assertFalse(payload.view().contains("\"seed\""));
            for (var chunk : top.skyeyefast.mchjong.network.ReplayPayload.split(
                    top.skyeyefast.mchjong.network.ReplayPayload.Kind.MATCH, "牌譜🀄".repeat(10_000))) {
                top.skyeyefast.mchjong.network.ReplayPayload.CODEC.encode(buffer, chunk);
                assertEquals(chunk, top.skyeyefast.mchjong.network.ReplayPayload.CODEC.decode(buffer));
            }
        } finally { buffer.release(); }
    }
}
