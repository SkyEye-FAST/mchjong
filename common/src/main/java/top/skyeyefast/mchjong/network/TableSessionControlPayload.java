package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Shared room exit, vote and last-player leave decisions. */
public record TableSessionControlPayload(BlockPos pos, UUID tableId, Operation operation, long token, boolean enabled)
        implements CustomPacketPayload {
    public enum Operation { REQUEST_EXIT, ANSWER_EXIT, RESOLVE_LEAVE }
    public static final Type<TableSessionControlPayload> TYPE = new Type<>(MahjongContent.id("table_session_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TableSessionControlPayload> CODEC = new StreamCodec<>() {
        @Override public TableSessionControlPayload decode(RegistryFriendlyByteBuf buffer) {
            return new TableSessionControlPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readEnum(Operation.class),
                buffer.readVarLong(), buffer.readBoolean());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, TableSessionControlPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeEnum(value.operation());
            buffer.writeVarLong(value.token()); buffer.writeBoolean(value.enabled());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
