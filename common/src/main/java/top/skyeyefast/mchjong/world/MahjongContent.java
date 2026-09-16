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

/** Object creation is shared; each loader registers these exact instances. */
public final class MahjongContent {
    public static final String MOD_ID = "mchjong";
    public static final MahjongTableBlock TABLE = new MahjongTableBlock(properties().noOcclusion());
    public static final TableSpaceBlock SPACE = new TableSpaceBlock(properties().noOcclusion().noLootTable());
    public static final MahjongStoolBlock STOOL = new MahjongStoolBlock(properties().noOcclusion());
    public static final Item TABLE_ITEM = new MahjongTableItem(TABLE, new Item.Properties());
    public static final Item STOOL_ITEM = new BlockItem(STOOL, new Item.Properties());
    public static BlockEntityType<MahjongTableBlockEntity> TABLE_ENTITY;
    public static EntityType<SeatEntity> SEAT_ENTITY;

    private MahjongContent() {}

    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD)
            .pushReaction(PushReaction.BLOCK);
    }

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MOD_ID, path); }
}
