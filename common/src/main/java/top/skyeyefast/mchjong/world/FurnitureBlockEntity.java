package top.skyeyefast.mchjong.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import top.skyeyefast.mchjong.item.FurnitureWood;
import top.skyeyefast.mchjong.item.MahjongComponents;

/** Public furniture appearance only. Subclasses must never add private state to appearanceTag. */
public class FurnitureBlockEntity extends BlockEntity {
    private FurnitureWood wood = FurnitureWood.OAK;
    private DyeColor color = DyeColor.WHITE;

    public FurnitureBlockEntity(BlockPos pos, BlockState state) { this(MahjongContent.STOOL_ENTITY, pos, state); }
    protected FurnitureBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    public FurnitureWood wood() { return wood; }
    public DyeColor color() { return color; }

    @Override protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        wood = input.getOrDefault(MahjongComponents.WOOD, FurnitureWood.OAK);
        color = input.getOrDefault(DataComponents.BASE_COLOR, DyeColor.WHITE);
    }
    @Override protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        builder.set(MahjongComponents.WOOD, wood);
        builder.set(DataComponents.BASE_COLOR, color);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeAppearance(tag);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (FurnitureWood candidate : FurnitureWood.values()) if (candidate.getSerializedName().equals(tag.getString("wood"))) wood = candidate;
        if (tag.contains("color")) color = DyeColor.byId(tag.getInt("color"));
    }
    protected void writeAppearance(CompoundTag tag) {
        tag.putString("wood", wood.getSerializedName());
        tag.putInt("color", color.getId());
    }
    public void appearanceChanged() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeAppearance(tag);
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
