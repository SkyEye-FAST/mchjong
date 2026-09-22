package top.skyeyefast.mchjong.world;

import net.minecraft.core.BlockPos;
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

    public void applyItem(net.minecraft.world.item.ItemStack stack) {
        wood = MahjongComponents.wood(stack);
        var value = MahjongComponents.color(stack);
        color = value == null ? DyeColor.WHITE : value;
        appearanceChanged();
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeAppearance(tag);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
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
    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        writeAppearance(tag);
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
