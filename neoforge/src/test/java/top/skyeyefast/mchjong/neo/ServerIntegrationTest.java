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
import top.skyeyefast.mchjong.engine.RiichiSession;
import top.skyeyefast.mchjong.engine.RiichiPreset;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.network.RiichiActionPayload;
import top.skyeyefast.mchjong.network.RiichiControlPayload;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.network.RiichiViewPayload;
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

    private static RiichiSession startedGame() {
        RiichiSession game = new RiichiSession(UUID.randomUUID(), RiichiPreset.MAHJONG_SOUL_4, 1892);
        for (int seat = 0; seat < 4; seat++) {
            UUID id = new UUID(5, seat);
            game.join(id, "Player " + seat, seat);
        }
        var host = new UUID(5, 0);
        var lobby = game.roomView(host);
        int begin = java.util.stream.IntStream.range(0, lobby.actions().size())
            .filter(i -> lobby.actions().get(i).type() == top.skyeyefast.mchjong.engine.RoomAction.Type.BEGIN_SEATING).findFirst().orElseThrow();
        assertTrue(game.actRoom(host, lobby.tableId(), lobby.incarnation(), lobby.decision(), begin));
        for (int seat = 0; seat < 4; seat++) {
            UUID id = new UUID(5, seat);
            assertTrue(game.join(id, "Player " + seat, game.seatOf(id)));
            var view = game.roomView(id);
            int ready = -1;
            for (int i = 0; i < view.actions().size(); i++)
                if (view.actions().get(i).type() == top.skyeyefast.mchjong.engine.RoomAction.Type.READY) ready = i;
            assertTrue(game.actRoom(id, view.tableId(), view.incarnation(), view.decision(), ready));
        }
        game.validate();
        return game;
    }

    @Test void privateSavedHandsNeverEnterAChunkUpdate(MinecraftServer server) {
        RiichiSession game = startedGame();
        CompoundTag saved = new CompoundTag();
        saved.putString("session", top.skyeyefast.mchjong.engine.TableSessionCodec.save(game));
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
        assertTrue(restored.contains("session"));
        assertTrue(restored.contains("boxes"));
        assertTrue(restored.contains("cloth"));
        RiichiSession copy = (RiichiSession) top.skyeyefast.mchjong.engine.TableSessionCodec.restore(restored.getString("session"));
        assertEquals(game.view(null).seats(), copy.view(null).seats());
        assertEquals(game.view(null).wall(), copy.view(null).wall());
        CompoundTag appearance = table.getUpdateTag(server.registryAccess());
        assertEquals(java.util.Set.of("wood", "color", "cloth_color", "tile_material", "tile_back", "tile_preset", "tile_back_preset"), appearance.getAllKeys());
        assertEquals("glass", appearance.getString("tile_material"));
        assertEquals("mchjong:kanto", appearance.getString("tile_preset"));
        assertEquals("mchjong:default", appearance.getString("tile_back_preset"));
        table.loadWithComponents(appearance, server.registryAccess());
        CompoundTag afterPublicUpdate = table.saveWithoutMetadata(server.registryAccess());
        assertEquals(restored.getString("session"), afterPublicUpdate.getString("session"));
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
            var request = new RiichiActionPayload(new BlockPos(-5, 72, 9), UUID.randomUUID(), 17, 3);
            RiichiActionPayload.CODEC.encode(buffer, request);
            assertEquals(request, RiichiActionPayload.CODEC.decode(buffer));
            var seatRequest = new top.skyeyefast.mchjong.network.TableSeatPayload(request.pos(), request.tableId());
            top.skyeyefast.mchjong.network.TableSeatPayload.CODEC.encode(buffer, seatRequest);
            assertEquals(seatRequest, top.skyeyefast.mchjong.network.TableSeatPayload.CODEC.decode(buffer));
            var handOrder = new top.skyeyefast.mchjong.network.RiichiHandOrderPayload(
                request.pos(), request.tableId(), request.decision(), 13, 27, true);
            top.skyeyefast.mchjong.network.RiichiHandOrderPayload.CODEC.encode(buffer, handOrder);
            assertEquals(handOrder, top.skyeyefast.mchjong.network.RiichiHandOrderPayload.CODEC.decode(buffer));
            for (var operation : RiichiControlPayload.Operation.values()) for (boolean enabled : new boolean[]{false, true}) {
                var control = new RiichiControlPayload(new BlockPos(-6, 70, 20), UUID.randomUUID(), operation, Long.MAX_VALUE, enabled);
                RiichiControlPayload.CODEC.encode(buffer, control);
                assertEquals(control, RiichiControlPayload.CODEC.decode(buffer));
            }
            for (var operation : top.skyeyefast.mchjong.network.TableSessionControlPayload.Operation.values()) {
                var control = new top.skyeyefast.mchjong.network.TableSessionControlPayload(
                    request.pos(), request.tableId(), operation, 23, true);
                top.skyeyefast.mchjong.network.TableSessionControlPayload.CODEC.encode(buffer, control);
                assertEquals(control, top.skyeyefast.mchjong.network.TableSessionControlPayload.CODEC.decode(buffer));
            }
            for (boolean maximum : new boolean[]{false, true}) {
                var rules = RiichiPreset.WRC.config();
                for (var option : top.skyeyefast.mchjong.engine.RiichiRuleOption.values())
                    rules = rules.with(option, maximum ? option.max() : option.min());
                var proposal = new top.skyeyefast.mchjong.network.RiichiRulesPayload(request.pos(), request.tableId(), Long.MAX_VALUE, rules);
                top.skyeyefast.mchjong.network.RiichiRulesPayload.CODEC.encode(buffer, proposal);
                assertEquals(proposal, top.skyeyefast.mchjong.network.RiichiRulesPayload.CODEC.decode(buffer));
            }
            RiichiSession game = startedGame();
            var payload = new RiichiViewPayload(BlockPos.ZERO, TableNetworking.JSON.toJson(game.view(null)), false, true, false, 63,
                game.roomView(null), game.roomSettings(),
                new top.skyeyefast.mchjong.world.BotServiceState(null, java.util.Arrays.asList(null, null, null, null)),
                top.skyeyefast.mchjong.world.WorldSettings.Policy.DEFAULT, top.skyeyefast.mchjong.engine.MahjongVariant.RIICHI);
            RiichiViewPayload.CODEC.encode(buffer, payload);
            assertEquals(payload, RiichiViewPayload.CODEC.decode(buffer));
            RiichiView decoded = TableNetworking.JSON.fromJson(payload.view(), RiichiView.class);
            assertTrue(decoded.actions().isEmpty());
            assertTrue(decoded.seats().stream().flatMap(seat -> seat.hand().stream()).allMatch(tile -> tile == -1));
            assertFalse(payload.view().contains("\"seed\""));
            var choice = new top.skyeyefast.mchjong.network.TableVariantPayload(request.pos(), request.tableId(), 17,
                top.skyeyefast.mchjong.engine.MahjongVariant.MCR);
            top.skyeyefast.mchjong.network.TableVariantPayload.CODEC.encode(buffer, choice);
            assertEquals(choice, top.skyeyefast.mchjong.network.TableVariantPayload.CODEC.decode(buffer));
            var action = new top.skyeyefast.mchjong.network.McrActionPayload(request.pos(), request.tableId(), UUID.randomUUID(), 9, 2);
            top.skyeyefast.mchjong.network.McrActionPayload.CODEC.encode(buffer, action);
            assertEquals(action, top.skyeyefast.mchjong.network.McrActionPayload.CODEC.decode(buffer));
            assertThrows(IllegalArgumentException.class, () -> new top.skyeyefast.mchjong.network.McrActionPayload(
                request.pos(), request.tableId(), UUID.randomUUID(), 9, -1));
            var roomAction = new top.skyeyefast.mchjong.network.TableRoomActionPayload(request.pos(), request.tableId(),
                UUID.randomUUID(), 9, 2);
            top.skyeyefast.mchjong.network.TableRoomActionPayload.CODEC.encode(buffer, roomAction);
            assertEquals(roomAction, top.skyeyefast.mchjong.network.TableRoomActionPayload.CODEC.decode(buffer));
            var lifecycle = new top.skyeyefast.mchjong.network.McrNextHandPayload(request.pos(), request.tableId(),
                UUID.randomUUID(), 9);
            top.skyeyefast.mchjong.network.McrNextHandPayload.CODEC.encode(buffer, lifecycle);
            assertEquals(lifecycle, top.skyeyefast.mchjong.network.McrNextHandPayload.CODEC.decode(buffer));
            var roster = java.util.stream.IntStream.range(0, 4).mapToObj(seat ->
                new top.skyeyefast.mchjong.engine.TableParticipant(UUID.randomUUID(), "Player " + seat)).toList();
            var session = top.skyeyefast.mchjong.engine.McrSession.start(request.tableId(), roster, 7,
                top.skyeyefast.mchjong.engine.Tile.mcrSet());
            var spectator = session.view(UUID.randomUUID());
            var mcrView = new top.skyeyefast.mchjong.network.McrViewPayload(request.pos(),
                top.skyeyefast.mchjong.engine.McrCodec.encodeSessionView(spectator),
                session.roomView(UUID.randomUUID()),
                new top.skyeyefast.mchjong.item.McrDeck(top.skyeyefast.mchjong.item.TileMaterial.BONE,
                    net.minecraft.world.item.DyeColor.BLUE, top.skyeyefast.mchjong.item.TileFacePreset.KANSAI,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mchjong", "default")),
                net.minecraft.world.item.DyeColor.CYAN, true, false);
            top.skyeyefast.mchjong.network.McrViewPayload.CODEC.encode(buffer, mcrView);
            assertEquals(mcrView, top.skyeyefast.mchjong.network.McrViewPayload.CODEC.decode(buffer));
            assertTrue(top.skyeyefast.mchjong.engine.McrCodec.decodeSessionView(mcrView.view()).game().actions().isEmpty());
            assertFalse(mcrView.view().contains("seed"));
            for (var chunk : top.skyeyefast.mchjong.network.ReplayPayload.split(
                    top.skyeyefast.mchjong.network.ReplayPayload.Kind.MATCH, "牌譜🀄".repeat(10_000))) {
                top.skyeyefast.mchjong.network.ReplayPayload.CODEC.encode(buffer, chunk);
                assertEquals(chunk, top.skyeyefast.mchjong.network.ReplayPayload.CODEC.decode(buffer));
            }
        } finally { buffer.release(); }
    }

    @Test void mcrSessionSurvivesPrivateTableSaveAndPublicAppearanceUpdate(MinecraftServer server) {
        UUID tableId = UUID.randomUUID();
        var source = new MahjongTableBlockEntity(BlockPos.ZERO, MahjongContent.AUTO_TABLE.defaultBlockState());
        source.equipment().boxes().setItem(0, top.skyeyefast.mchjong.item.MahjongSupplies.stockedBox(
            top.skyeyefast.mchjong.engine.RedFives.NONE));
        source.equipment().installCloth(new net.minecraft.world.item.ItemStack(MahjongContent.CLOTH_ITEM));
        var roster = java.util.stream.IntStream.range(0, 4).mapToObj(seat ->
            new top.skyeyefast.mchjong.engine.TableParticipant(UUID.randomUUID(), "Player " + seat)).toList();
        var session = top.skyeyefast.mchjong.engine.McrSession.start(tableId, roster, 7,
            source.equipment().mcrStock().deck().tiles());
        var privateTag = source.saveWithoutMetadata(server.registryAccess());
        privateTag.putString("session", top.skyeyefast.mchjong.engine.TableSessionCodec.save(session));
        var table = new MahjongTableBlockEntity(BlockPos.ZERO, MahjongContent.AUTO_TABLE.defaultBlockState());
        table.loadWithComponents(privateTag, server.registryAccess());
        var saved = table.saveWithoutMetadata(server.registryAccess());
        assertEquals(top.skyeyefast.mchjong.engine.MahjongVariant.MCR,
            top.skyeyefast.mchjong.engine.TableSessionCodec.restore(saved.getString("session")).variant());
        assertNotEquals(privateTag.getString("session"), saved.getString("session"));
        assertEquals(144, table.equipment().mcrStock().deck().tiles().size());
        var appearance = table.getUpdateTag(server.registryAccess());
        assertFalse(appearance.contains("session"));
        table.loadWithComponents(appearance, server.registryAccess());
        assertEquals(saved.getString("session"), table.saveWithoutMetadata(server.registryAccess()).getString("session"));
    }
}
