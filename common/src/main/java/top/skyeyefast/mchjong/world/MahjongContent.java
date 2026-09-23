package top.skyeyefast.mchjong.world;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.component.ItemContainerContents;
import top.skyeyefast.mchjong.item.FurnitureWood;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplyItem;
import top.skyeyefast.mchjong.item.MahjongBoxItem;
import top.skyeyefast.mchjong.item.TileData;

/** Object creation is shared; each loader registers these exact instances. */
public final class MahjongContent {
    public static final String MOD_ID = "mchjong";
    public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("mchjong"));
    public static final MahjongTableBlock TABLE = new MahjongTableBlock(properties("mahjong_table").noOcclusion());
    public static final MahjongTableBlock AUTO_TABLE = new MahjongTableBlock(properties("automatic_mahjong_table").noOcclusion().sound(SoundType.METAL));
    public static final TableSpaceBlock SPACE = new TableSpaceBlock(properties("table_space").noOcclusion().noLootTable());
    public static final MahjongStoolBlock STOOL = new MahjongStoolBlock(properties("mahjong_stool").noOcclusion());
    public static final Item TABLE_ITEM = new MahjongTableItem(TABLE, furniture("mahjong_table"));
    public static final Item AUTO_TABLE_ITEM = new MahjongTableItem(AUTO_TABLE, furniture("automatic_mahjong_table"));
    public static final Item STOOL_ITEM = new BlockItem(STOOL, furniture("mahjong_stool").component(DataComponents.BASE_COLOR, DyeColor.WHITE));
    public static final Item CLOTH_ITEM = new MahjongSupplyItem(item("table_cloth").component(DataComponents.BASE_COLOR, DyeColor.CYAN));
    public static final Item TILE_ITEM = new MahjongSupplyItem(item("mahjong_tile").component(MahjongComponents.TILE, TileData.BLANK)
        .component(MahjongComponents.FACE_PRESET, top.skyeyefast.mchjong.item.TileFacePreset.KANSAI));
    public static final Item POINT_STICK = new MahjongSupplyItem(item("point_stick").component(MahjongComponents.POINTS, 0));
    public static final Item DICE = new Item(item("dice"));
    public static final Item BOX_ITEM = new MahjongBoxItem(item("mahjong_box").stacksTo(1).component(DataComponents.CONTAINER, ItemContainerContents.EMPTY));
    public static final Item MAHJONG_DYE = new Item(item("mahjong_dye").stacksTo(64));
    public static final Item RED_DORA_DYE = new Item(item("red_dora_dye").stacksTo(64));
    public static final Item UNDO_DYE = new Item(item("undo_dye").stacksTo(64));
    public static final Item CREATIVE_MAHJONG_DYE = new Item(item("creative_mahjong_dye").stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC));
    public static final java.util.Map<String, Item> SUPPLIES = java.util.Map.of(
        "table_cloth", CLOTH_ITEM, "mahjong_tile", TILE_ITEM, "point_stick", POINT_STICK, "mahjong_box", BOX_ITEM,
        "mahjong_dye", MAHJONG_DYE, "creative_mahjong_dye", CREATIVE_MAHJONG_DYE, "red_dora_dye", RED_DORA_DYE,
        "undo_dye", UNDO_DYE, "dice", DICE);
    public static BlockEntityType<MahjongTableBlockEntity> TABLE_ENTITY;
    public static BlockEntityType<FurnitureBlockEntity> STOOL_ENTITY;
    public static EntityType<SeatEntity> SEAT_ENTITY;
    public static final net.minecraft.world.inventory.MenuType<top.skyeyefast.mchjong.item.MahjongBoxMenu> BOX_MENU =
        new net.minecraft.world.inventory.MenuType<>(top.skyeyefast.mchjong.item.MahjongBoxMenu::new, net.minecraft.world.flag.FeatureFlags.VANILLA_SET);
    public static final net.minecraft.world.inventory.MenuType<top.skyeyefast.mchjong.item.MahjongTableMenu> TABLE_MENU =
        new net.minecraft.world.inventory.MenuType<>(top.skyeyefast.mchjong.item.MahjongTableMenu::new, net.minecraft.world.flag.FeatureFlags.VANILLA_SET);
    public static final net.minecraft.world.inventory.MenuType<top.skyeyefast.mchjong.item.PointStickMenu> STICK_MENU =
        new net.minecraft.world.inventory.MenuType<>(top.skyeyefast.mchjong.item.PointStickMenu::new, net.minecraft.world.flag.FeatureFlags.VANILLA_SET);

    private MahjongContent() {}

    private static Item.Properties furniture(String name) {
        return item(name).useBlockDescriptionPrefix().component(MahjongComponents.WOOD, FurnitureWood.OAK);
    }

    private static Item.Properties item(String name) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id(name)));
    }

    private static BlockBehaviour.Properties properties(String name) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD)
            .pushReaction(PushReaction.BLOCK).setId(ResourceKey.create(Registries.BLOCK, id(name)));
    }

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }
}
