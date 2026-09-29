package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.world.MahjongContent;

/** An issued action index, or -1 for next-hand confirmation; the sender's UUID determines the seat. */
public record McrActionPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision, int actionIndex)
    implements CustomPacketPayload {
    public McrActionPayload {
        if (actionIndex < -1) throw new IllegalArgumentException("Invalid MCR action index");
    }
    public static final Type<McrActionPayload> TYPE = new Type<>(MahjongContent.id("mcr_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, McrActionPayload> CODEC = new StreamCodec<>() {
        @Override public McrActionPayload decode(RegistryFriendlyByteBuf buffer) {
            return new McrActionPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(), buffer.readVarLong(), buffer.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, McrActionPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeUUID(value.incarnation());
            buffer.writeVarLong(value.decision()); buffer.writeVarInt(value.actionIndex());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
