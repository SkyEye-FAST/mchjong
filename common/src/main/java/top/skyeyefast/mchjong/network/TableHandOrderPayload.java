package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.world.MahjongContent;

/** A physical tile move within the sender's private hand; the server validates ownership and the decision. */
public record TableHandOrderPayload(BlockPos pos, UUID tableId, long decision, int source, int target, boolean after)
        implements CustomPacketPayload {
    public static final Type<TableHandOrderPayload> TYPE = new Type<>(MahjongContent.id("table_hand_order"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TableHandOrderPayload> CODEC = new StreamCodec<>() {
        @Override public TableHandOrderPayload decode(RegistryFriendlyByteBuf buffer) {
            return new TableHandOrderPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readVarLong(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, TableHandOrderPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeVarLong(value.decision());
            buffer.writeVarInt(value.source()); buffer.writeVarInt(value.target()); buffer.writeBoolean(value.after());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
