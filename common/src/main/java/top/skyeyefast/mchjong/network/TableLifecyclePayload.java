package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Session progression is separate from room preparation and rule decisions. */
public record TableLifecyclePayload(BlockPos pos, UUID tableId, UUID incarnation, long decision, Operation operation)
    implements CustomPacketPayload {
    public enum Operation { CONFIRM_NEXT_HAND }
    public TableLifecyclePayload {
        java.util.Objects.requireNonNull(operation);
    }
    public static final Type<TableLifecyclePayload> TYPE = new Type<>(MahjongContent.id("table_lifecycle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TableLifecyclePayload> CODEC = new StreamCodec<>() {
        @Override public TableLifecyclePayload decode(RegistryFriendlyByteBuf buffer) {
            return new TableLifecyclePayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(),
                buffer.readVarLong(), buffer.readEnum(Operation.class));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, TableLifecyclePayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeUUID(value.incarnation());
            buffer.writeVarLong(value.decision()); buffer.writeEnum(value.operation());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
