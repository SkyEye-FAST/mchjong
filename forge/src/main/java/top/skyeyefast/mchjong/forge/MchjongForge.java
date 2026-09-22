package top.skyeyefast.mchjong.forge;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import top.skyeyefast.mchjong.item.MahjongCatalog;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileData;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;

@Mod(MahjongContent.MOD_ID)
public final class MchjongForge {
    public MchjongForge(FMLJavaModLoadingContext context) {
        var bus = context.getModEventBus();
        MinecraftForge.EVENT_BUS.addListener((ServerStartingEvent event) ->
            top.skyeyefast.mchjong.world.WorldSettings.of(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
            top.skyeyefast.mchjong.world.TableCommands.register(event.getDispatcher()));
        var components = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MahjongContent.MOD_ID);
        top.skyeyefast.mchjong.item.MahjongComponents.TYPES.forEach((name, type) -> components.register(name, () -> type));
        components.register(bus);
        var recipes = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MahjongContent.MOD_ID);
        top.skyeyefast.mchjong.recipe.MahjongRecipes.SERIALIZERS.forEach((name, serializer) -> recipes.register(name, () -> serializer));
        recipes.register(bus);
        var sounds = DeferredRegister.create(Registries.SOUND_EVENT, MahjongContent.MOD_ID);
        top.skyeyefast.mchjong.world.MahjongSounds.EVENTS.forEach((name, sound) -> sounds.register(name, () -> sound));
        sounds.register(bus);
        DeferredRegister<Block> blocks = DeferredRegister.create(Registries.BLOCK, MahjongContent.MOD_ID);
        blocks.register("mahjong_table", () -> MahjongContent.TABLE);
        blocks.register("automatic_mahjong_table", () -> MahjongContent.AUTO_TABLE);
        blocks.register("table_space", () -> MahjongContent.SPACE);
        blocks.register("mahjong_stool", () -> MahjongContent.STOOL);
        blocks.register(bus);
        // Defer shared object creation until Forge opens intrusive holders.
        DeferredRegister<Item> items = DeferredRegister.create(Registries.ITEM, MahjongContent.MOD_ID);
        items.register("mahjong_table", () -> MahjongContent.TABLE_ITEM);
        items.register("automatic_mahjong_table", () -> MahjongContent.AUTO_TABLE_ITEM);
        items.register("mahjong_stool", () -> MahjongContent.STOOL_ITEM);
        for (String name : new String[] {"table_cloth", "mahjong_tile", "point_stick", "mahjong_box",
            "mahjong_dye", "creative_mahjong_dye", "red_dora_dye", "undo_dye", "dice"}) {
            items.register(name, () -> MahjongContent.SUPPLIES.get(name));
        }
        items.register(bus);
        var menus = DeferredRegister.create(Registries.MENU, MahjongContent.MOD_ID);
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
            .sized(0.3f, 0.1f).noSave().clientTrackingRange(10).updateInterval(10).build("mchjong:seat"));
        entities.register(bus);
        DeferredRegister<CreativeModeTab> tabs = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MahjongContent.MOD_ID);
        tabs.register("mchjong", () -> CreativeModeTab.builder()
            .icon(() -> MahjongSupplies.tile(new TileData(32, TileMaterial.BONE, false), 1))
            .title(Component.translatable("itemGroup.mchjong"))
            .displayItems((params, output) -> MahjongCatalog.entries().forEach(output::accept)).build());
        tabs.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) MahjongCatalog.entries().forEach(event::accept);
        });
        bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) ->
            event.enqueueWork(ForgeNetworking::register));
    }
}
