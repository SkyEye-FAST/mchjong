package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.world.MahjongContent;

/** No client-supplied tiles, scores, usernames or seat ownership. */
public record RiichiActionPayload(BlockPos pos, UUID tableId, long decision, int action) implements CustomPacketPayload {
    public static final Type<RiichiActionPayload> TYPE = new Type<>(MahjongContent.id("riichi_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RiichiActionPayload> CODEC = new StreamCodec<>() {
        @Override public RiichiActionPayload decode(RegistryFriendlyByteBuf buffer) {
            return new RiichiActionPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readVarLong(), buffer.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, RiichiActionPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId());
            buffer.writeVarLong(value.decision()); buffer.writeVarInt(value.action());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
