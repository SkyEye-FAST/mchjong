package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.world.MahjongContent;

/** An index into the recipient's current TableRoomView actions. */
public record TableRoomActionPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision, int actionIndex)
    implements CustomPacketPayload {
    public TableRoomActionPayload {
        if (actionIndex < 0) throw new IllegalArgumentException("Invalid room action index");
    }
    public static final Type<TableRoomActionPayload> TYPE = new Type<>(MahjongContent.id("table_room_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TableRoomActionPayload> CODEC = new StreamCodec<>() {
        @Override public TableRoomActionPayload decode(RegistryFriendlyByteBuf buffer) {
            return new TableRoomActionPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(),
                buffer.readVarLong(), buffer.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, TableRoomActionPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeUUID(value.incarnation());
            buffer.writeVarLong(value.decision()); buffer.writeVarInt(value.actionIndex());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
