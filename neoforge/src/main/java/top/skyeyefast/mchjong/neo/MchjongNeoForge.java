package top.skyeyefast.mchjong.neo;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
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
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileData;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.network.TableActionPayload;
import top.skyeyefast.mchjong.network.TableControlPayload;
import top.skyeyefast.mchjong.network.TableNetworking;
import top.skyeyefast.mchjong.network.TableSeatPayload;
import top.skyeyefast.mchjong.network.TableViewPayload;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;

@Mod(MahjongContent.MOD_ID)
public final class MchjongNeoForge {
    public MchjongNeoForge(IEventBus bus) {
        if (net.neoforged.fml.ModList.get().isLoaded("create"))
            top.skyeyefast.mchjong.compat.create.CreateCompat.register(bus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.server.ServerStartingEvent event) -> {
                top.skyeyefast.mchjong.world.WorldSettings.of(event.getServer());
                top.skyeyefast.mchjong.config.ServerPresets.load(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get());
            });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
                if (event.getEntity() instanceof ServerPlayer player)
                    top.skyeyefast.mchjong.config.ServerPresets.send(player);
            });
        DeferredRegister<net.minecraft.core.component.DataComponentType<?>> components = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MahjongContent.MOD_ID);
        top.skyeyefast.mchjong.item.MahjongComponents.TYPES.forEach((name, type) -> components.register(name, () -> type));
        components.register(bus);
        DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> recipes = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MahjongContent.MOD_ID);
        top.skyeyefast.mchjong.recipe.MahjongRecipes.SERIALIZERS.forEach((name, serializer) -> recipes.register(name, () -> serializer));
        recipes.register(bus);
        DeferredRegister<net.minecraft.sounds.SoundEvent> sounds = DeferredRegister.create(Registries.SOUND_EVENT, MahjongContent.MOD_ID);
        top.skyeyefast.mchjong.world.MahjongSounds.EVENTS.forEach((name, event) -> sounds.register(name, () -> event));
        sounds.register(bus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.RegisterCommandsEvent event) ->
                top.skyeyefast.mchjong.world.TableCommands.register(event.getDispatcher()));
        DeferredRegister<Block> blocks = DeferredRegister.create(Registries.BLOCK, MahjongContent.MOD_ID);
        blocks.register("mahjong_table", () -> MahjongContent.TABLE);
        blocks.register("automatic_mahjong_table", () -> MahjongContent.AUTO_TABLE);
        blocks.register("table_space", () -> MahjongContent.SPACE);
        blocks.register("mahjong_stool", () -> MahjongContent.STOOL);
        blocks.register(bus);
        DeferredRegister<Item> items = DeferredRegister.create(Registries.ITEM, MahjongContent.MOD_ID);
        items.register("mahjong_table", () -> MahjongContent.TABLE_ITEM);
        items.register("automatic_mahjong_table", () -> MahjongContent.AUTO_TABLE_ITEM);
        // Do not initialize MahjongContent until the block registry event opens intrusive holders.
        items.register("table_cloth", () -> MahjongContent.CLOTH_ITEM);
        items.register("mahjong_tile", () -> MahjongContent.TILE_ITEM);
        items.register("mahjong_box", () -> MahjongContent.BOX_ITEM);
        items.register("point_stick", () -> MahjongContent.POINT_STICK);
        items.register("dice", () -> MahjongContent.DICE);
        items.register("mahjong_dye", () -> MahjongContent.MAHJONG_DYE);
        items.register("red_dora_dye", () -> MahjongContent.RED_DORA_DYE);
        items.register("undo_dye", () -> MahjongContent.UNDO_DYE);
        items.register("creative_mahjong_dye", () -> MahjongContent.CREATIVE_MAHJONG_DYE);
        items.register("mahjong_stool", () -> MahjongContent.STOOL_ITEM);
        items.register(bus);
        DeferredRegister<net.minecraft.world.inventory.MenuType<?>> menus = DeferredRegister.create(Registries.MENU, MahjongContent.MOD_ID);
        menus.register("mahjong_box", () -> MahjongContent.BOX_MENU);
        menus.register("mahjong_table", () -> MahjongContent.TABLE_MENU);
        menus.register("point_sticks", () -> MahjongContent.STICK_MENU);
        menus.register(bus);
        DeferredRegister<BlockEntityType<?>> blockEntities = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MahjongContent.MOD_ID);
        blockEntities.register("mahjong_table", () -> MahjongContent.TABLE_ENTITY =
            BlockEntityType.Builder.of(MahjongTableBlockEntity::new, MahjongContent.TABLE, MahjongContent.AUTO_TABLE).build(null));
        blockEntities.register("mahjong_stool", () -> MahjongContent.STOOL_ENTITY =
            BlockEntityType.Builder.of(top.skyeyefast.mchjong.world.FurnitureBlockEntity::new, MahjongContent.STOOL).build(null));
        blockEntities.register(bus);
        DeferredRegister<EntityType<?>> entities = DeferredRegister.create(Registries.ENTITY_TYPE, MahjongContent.MOD_ID);
        entities.register("seat", () -> MahjongContent.SEAT_ENTITY = EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC)
            .sized(0.3f, 0.1f).clientTrackingRange(10).updateInterval(10).build("mchjong:seat"));
        entities.register(bus);
        DeferredRegister<CreativeModeTab> tabs = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MahjongContent.MOD_ID);
        tabs.register("mchjong", () -> CreativeModeTab.builder()
            .icon(() -> MahjongSupplies.tile(new TileData(32, TileMaterial.BONE, false), 1))
            .title(Component.translatable("itemGroup.mchjong"))
            .displayItems((params, output) -> top.skyeyefast.mchjong.item.MahjongCatalog.entries().forEach(output::accept))
            .build());
        tabs.register(bus);
        bus.addListener(this::payloads);
        bus.addListener(this::creativeTab);
    }

    private void payloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("11");
        registrar.playToServer(top.skyeyefast.mchjong.network.BoxPrintPayload.TYPE, top.skyeyefast.mchjong.network.BoxPrintPayload.CODEC,
            (payload, context) -> { if (context.player() instanceof ServerPlayer player) payload.handle(player); });
        registrar.playToServer(top.skyeyefast.mchjong.network.StickChoicePayload.TYPE, top.skyeyefast.mchjong.network.StickChoicePayload.CODEC,
            (payload, context) -> { if (context.player() instanceof ServerPlayer player) payload.handle(player); });
        registrar.playToServer(TableActionPayload.TYPE, TableActionPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) TableNetworking.receive(player, payload);
        });
        registrar.playToServer(TableControlPayload.TYPE, TableControlPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) TableNetworking.receive(player, payload);
        });
        registrar.playToServer(TableSeatPayload.TYPE, TableSeatPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) TableNetworking.receive(player, payload);
        });
        registrar.playToServer(top.skyeyefast.mchjong.network.TableRulesPayload.TYPE, top.skyeyefast.mchjong.network.TableRulesPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) TableNetworking.receive(player, payload);
        });
        registrar.playToServer(top.skyeyefast.mchjong.network.TableVisibilityPayload.TYPE, top.skyeyefast.mchjong.network.TableVisibilityPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) TableNetworking.receive(player, payload);
        });
        registrar.playToClient(TableViewPayload.TYPE, TableViewPayload.CODEC,
            (payload, context) -> ClientTableNetworking.receive(payload));
        registrar.playToClient(top.skyeyefast.mchjong.network.ReplayPayload.TYPE, top.skyeyefast.mchjong.network.ReplayPayload.CODEC,
            (payload, context) -> top.skyeyefast.mchjong.client.ClientReplays.receive(payload));
        registrar.playToClient(top.skyeyefast.mchjong.network.PresetBundlePayload.TYPE, top.skyeyefast.mchjong.network.PresetBundlePayload.CODEC,
            (payload, context) -> top.skyeyefast.mchjong.client.TileFacePresets.receive(payload));
        registrar.playToClient(top.skyeyefast.mchjong.network.StickAppearancePayload.TYPE, top.skyeyefast.mchjong.network.StickAppearancePayload.CODEC,
            (payload, context) -> top.skyeyefast.mchjong.client.RiichiStickPresets.receive(payload));
    }

    private void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            top.skyeyefast.mchjong.item.MahjongCatalog.entries().forEach(event::accept);
        }
    }
}
