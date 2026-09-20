package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Requests the server-assigned seat. The target seat is never supplied by the client. */
public record TableSeatPayload(BlockPos pos, UUID tableId) implements CustomPacketPayload {
    public static final Type<TableSeatPayload> TYPE = new Type<>(MahjongContent.id("table_seat"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TableSeatPayload> CODEC = new StreamCodec<>() {
        @Override public TableSeatPayload decode(RegistryFriendlyByteBuf buffer) {
            return new TableSeatPayload(buffer.readBlockPos(), buffer.readUUID());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, TableSeatPayload value) {
            buffer.writeBlockPos(value.pos());
            buffer.writeUUID(value.tableId());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
