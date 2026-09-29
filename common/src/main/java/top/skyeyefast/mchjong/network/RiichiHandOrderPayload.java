package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.world.MahjongContent;

/** A physical tile move within the sender's private hand; the server validates ownership and the decision. */
public record RiichiHandOrderPayload(BlockPos pos, UUID tableId, long decision, int source, int target, boolean after)
        implements CustomPacketPayload {
    public static final Type<RiichiHandOrderPayload> TYPE = new Type<>(MahjongContent.id("riichi_hand_order"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RiichiHandOrderPayload> CODEC = new StreamCodec<>() {
        @Override public RiichiHandOrderPayload decode(RegistryFriendlyByteBuf buffer) {
            return new RiichiHandOrderPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readVarLong(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, RiichiHandOrderPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeVarLong(value.decision());
            buffer.writeVarInt(value.source()); buffer.writeVarInt(value.target()); buffer.writeBoolean(value.after());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
