package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Administrative table controls never occupy or renumber legal tile actions. */
public record TableControlPayload(BlockPos pos, UUID tableId, Operation operation, long token, boolean enabled)
        implements CustomPacketPayload {
    public enum Operation { REQUEST_EXIT, ANSWER_EXIT, AUTO_SORT, AUTO_WIN, NO_CALLS, AUTO_DISCARD, AUTO_KITA, RESOLVE_LEAVE }
    public static final Type<TableControlPayload> TYPE = new Type<>(MahjongContent.id("table_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TableControlPayload> CODEC = new StreamCodec<>() {
        @Override public TableControlPayload decode(RegistryFriendlyByteBuf buffer) {
            return new TableControlPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readEnum(Operation.class),
                buffer.readVarLong(), buffer.readBoolean());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, TableControlPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeEnum(value.operation());
            buffer.writeVarLong(value.token()); buffer.writeBoolean(value.enabled());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
