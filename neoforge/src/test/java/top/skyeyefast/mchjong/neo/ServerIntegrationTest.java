package top.skyeyefast.mchjong.neo;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.network.TableActionPayload;
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
        assertTrue(server.getRecipeManager().byKey(MahjongContent.id("mahjong_table")).isPresent());
        assertTrue(server.getRecipeManager().byKey(MahjongContent.id("mahjong_stool")).isPresent());
    }

    private static Game startedGame() {
        Game game = new Game(UUID.randomUUID(), RuleSet.MAHJONG_SOUL_4, 1892);
        for (int seat = 0; seat < 4; seat++) {
            UUID id = new UUID(5, seat);
            game.join(id, "Player " + seat, seat);
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
        MahjongTableBlockEntity table = new MahjongTableBlockEntity(BlockPos.ZERO, MahjongContent.TABLE.defaultBlockState());
        table.loadWithComponents(saved, server.registryAccess());
        CompoundTag restored = table.saveWithoutMetadata(server.registryAccess());
        assertTrue(restored.contains("game"));
        Game copy = TableNetworking.JSON.fromJson(restored.getString("game"), Game.class);
        copy.validate();
        assertEquals(TableNetworking.JSON.toJson(game.view(null)), TableNetworking.JSON.toJson(copy.view(null)));
        assertTrue(table.getUpdateTag(server.registryAccess()).isEmpty());
        assertNull(table.getUpdatePacket());
    }

    @Test void actualMinecraftCodecsRoundTripOnlyDeclaredPayloads(MinecraftServer server) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        try {
            var request = new TableActionPayload(new BlockPos(-5, 72, 9), UUID.randomUUID(), 17, 3);
            TableActionPayload.CODEC.encode(buffer, request);
            assertEquals(request, TableActionPayload.CODEC.decode(buffer));
            Game game = startedGame();
            var payload = new TableViewPayload(BlockPos.ZERO, TableNetworking.JSON.toJson(game.view(null)), false);
            TableViewPayload.CODEC.encode(buffer, payload);
            assertEquals(payload, TableViewPayload.CODEC.decode(buffer));
            TableView decoded = TableNetworking.JSON.fromJson(payload.view(), TableView.class);
            assertTrue(decoded.actions().isEmpty());
            assertTrue(decoded.seats().stream().flatMap(seat -> seat.hand().stream()).allMatch(tile -> tile == -1));
            assertFalse(payload.view().contains("\"seed\""));
        } finally { buffer.release(); }
    }
}
