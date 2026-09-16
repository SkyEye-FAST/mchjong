package top.skyeyefast.mchjong.neo;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import top.skyeyefast.mchjong.client.ClientTableNetworking;
import top.skyeyefast.mchjong.network.TableActionPayload;
import top.skyeyefast.mchjong.network.TableControlPayload;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.network.TableViewPayload;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;

@Mod(MahjongContent.MOD_ID)
public final class MchjongNeoForge {
    public MchjongNeoForge(IEventBus bus) {
        DeferredRegister<net.minecraft.sounds.SoundEvent> sounds = DeferredRegister.create(Registries.SOUND_EVENT, MahjongContent.MOD_ID);
        top.skyeyefast.mchjong.world.MahjongSounds.EVENTS.forEach((name, event) -> sounds.register(name, () -> event));
        sounds.register(bus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.RegisterCommandsEvent event) ->
                top.skyeyefast.mchjong.world.TableCommands.register(event.getDispatcher()));
        DeferredRegister<Block> blocks = DeferredRegister.create(Registries.BLOCK, MahjongContent.MOD_ID);
        blocks.register("mahjong_table", () -> MahjongContent.TABLE);
        blocks.register("table_space", () -> MahjongContent.SPACE);
        blocks.register("mahjong_stool", () -> MahjongContent.STOOL);
        blocks.register(bus);
        DeferredRegister<Item> items = DeferredRegister.create(Registries.ITEM, MahjongContent.MOD_ID);
        items.register("mahjong_table", () -> MahjongContent.TABLE_ITEM);
        items.register("mahjong_stool", () -> MahjongContent.STOOL_ITEM);
        items.register(bus);
        DeferredRegister<BlockEntityType<?>> blockEntities = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MahjongContent.MOD_ID);
        blockEntities.register("mahjong_table", () -> MahjongContent.TABLE_ENTITY =
            BlockEntityType.Builder.of(MahjongTableBlockEntity::new, MahjongContent.TABLE).build(null));
        blockEntities.register(bus);
        DeferredRegister<EntityType<?>> entities = DeferredRegister.create(Registries.ENTITY_TYPE, MahjongContent.MOD_ID);
        entities.register("seat", () -> MahjongContent.SEAT_ENTITY = EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC)
            .sized(0.3f, 0.1f).noSave().clientTrackingRange(10).updateInterval(10).build("mchjong:seat"));
        entities.register(bus);
        bus.addListener(this::payloads);
        bus.addListener(this::creativeTab);
    }

    private void payloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToServer(TableActionPayload.TYPE, TableActionPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) TableNetworking.receive(player, payload);
        });
        registrar.playToServer(TableControlPayload.TYPE, TableControlPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) TableNetworking.receive(player, payload);
        });
        registrar.playToClient(TableViewPayload.TYPE, TableViewPayload.CODEC,
            (payload, context) -> ClientTableNetworking.receive(payload));
        registrar.playToClient(top.skyeyefast.mchjong.network.ReplayPayload.TYPE, top.skyeyefast.mchjong.network.ReplayPayload.CODEC,
            (payload, context) -> top.skyeyefast.mchjong.client.ClientReplays.receive(payload));
    }

    private void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(MahjongContent.TABLE_ITEM); event.accept(MahjongContent.STOOL_ITEM);
        }
    }
}
