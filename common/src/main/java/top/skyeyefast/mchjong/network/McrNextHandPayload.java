package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.world.MahjongContent;

/** MCR completed-hand acknowledgement, bound to the current session and decision. */
public record McrNextHandPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision)
    implements CustomPacketPayload {
    public static final Type<McrNextHandPayload> TYPE = new Type<>(MahjongContent.id("mcr_next_hand"));
    public static final StreamCodec<RegistryFriendlyByteBuf, McrNextHandPayload> CODEC = new StreamCodec<>() {
        @Override public McrNextHandPayload decode(RegistryFriendlyByteBuf buffer) {
            return new McrNextHandPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(),
                buffer.readVarLong());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, McrNextHandPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeUUID(value.incarnation());
            buffer.writeVarLong(value.decision());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
