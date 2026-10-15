package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.world.MahjongContent;

/**
 * An issued game action index for MCR, Sichuan mahjong or Taiwanese mahjong. The table's own rule runtime
 * decides which game it belongs to, and the authenticated sender determines the seat.
 */
public record TableActionPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision, int actionIndex)
    implements MahjongPayload {
    public TableActionPayload {
        if (actionIndex < 0) throw new IllegalArgumentException("Invalid action index");
    }
    public static final ResourceLocation TYPE = MahjongContent.id("table_action");
    public static TableActionPayload decode(FriendlyByteBuf buffer) {
        return new TableActionPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(), buffer.readVarLong(), buffer.readVarInt());
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos); buffer.writeUUID(tableId); buffer.writeUUID(incarnation);
        buffer.writeVarLong(decision); buffer.writeVarInt(actionIndex);
    }
    @Override public ResourceLocation id() { return TYPE; }
}
