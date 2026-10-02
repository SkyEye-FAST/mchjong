package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.world.MahjongContent;

/** An index into the recipient's current TableRoomView actions. */
public record TableRoomActionPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision, int actionIndex)
    implements MahjongPayload {
    public TableRoomActionPayload {
        if (actionIndex < 0) throw new IllegalArgumentException("Invalid room action index");
    }
    public static final ResourceLocation TYPE = MahjongContent.id("table_room_action");
    public static TableRoomActionPayload decode(FriendlyByteBuf buffer) {
        return new TableRoomActionPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(),
            buffer.readVarLong(), buffer.readVarInt());
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos()); buffer.writeUUID(this.tableId()); buffer.writeUUID(this.incarnation());
        buffer.writeVarLong(this.decision()); buffer.writeVarInt(this.actionIndex());
    }
    @Override public ResourceLocation id() { return TYPE; }
}
