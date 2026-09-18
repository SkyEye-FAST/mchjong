package top.skyeyefast.mchjong.world;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.core.component.DataComponents;
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
    public static final MahjongTableBlock TABLE = new MahjongTableBlock(properties().noOcclusion());
    public static final MahjongTableBlock AUTO_TABLE = new MahjongTableBlock(properties().noOcclusion().sound(SoundType.METAL));
    public static final TableSpaceBlock SPACE = new TableSpaceBlock(properties().noOcclusion().noLootTable());
    public static final MahjongStoolBlock STOOL = new MahjongStoolBlock(properties().noOcclusion());
    public static final Item TABLE_ITEM = new MahjongTableItem(TABLE, furniture());
    public static final Item AUTO_TABLE_ITEM = new MahjongTableItem(AUTO_TABLE, furniture());
    public static final Item STOOL_ITEM = new BlockItem(STOOL, furniture().component(DataComponents.BASE_COLOR, DyeColor.WHITE));
    public static final Item CLOTH_ITEM = new MahjongSupplyItem(new Item.Properties().component(DataComponents.BASE_COLOR, DyeColor.GREEN));
    public static final Item TILE_ITEM = new MahjongSupplyItem(new Item.Properties().component(MahjongComponents.TILE, TileData.BLANK)
        .component(MahjongComponents.FACE_PRESET, top.skyeyefast.mchjong.item.TileFacePreset.KANSAI)
        .component(DataComponents.BASE_COLOR, DyeColor.BLUE));
    public static final Item POINT_STICK = new MahjongSupplyItem(new Item.Properties().component(MahjongComponents.POINTS, 0));
    public static final Item BOX_ITEM = new MahjongBoxItem(new Item.Properties().stacksTo(1).component(DataComponents.CONTAINER, ItemContainerContents.EMPTY));
    public static final Item MAHJONG_DYE = new Item(new Item.Properties().stacksTo(64));
    public static final Item RED_DORA_DYE = new Item(new Item.Properties().stacksTo(64));
    public static final Item CREATIVE_MAHJONG_DYE = new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC));
    public static final java.util.Map<String, Item> SUPPLIES = java.util.Map.of(
        "table_cloth", CLOTH_ITEM, "mahjong_tile", TILE_ITEM, "point_stick", POINT_STICK, "mahjong_box", BOX_ITEM,
        "mahjong_dye", MAHJONG_DYE, "creative_mahjong_dye", CREATIVE_MAHJONG_DYE, "red_dora_dye", RED_DORA_DYE);
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

    private static Item.Properties furniture() { return new Item.Properties().component(MahjongComponents.WOOD, FurnitureWood.OAK); }

    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD)
            .pushReaction(PushReaction.BLOCK);
    }

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MOD_ID, path); }
}
