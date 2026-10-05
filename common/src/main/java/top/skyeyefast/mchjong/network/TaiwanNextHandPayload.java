package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.world.MahjongContent;

public record TaiwanNextHandPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision)
    implements CustomPacketPayload {
    public static final Type<TaiwanNextHandPayload> TYPE = new Type<>(MahjongContent.id("taiwan_next_hand"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TaiwanNextHandPayload> CODEC = new StreamCodec<>() {
        @Override public TaiwanNextHandPayload decode(RegistryFriendlyByteBuf buffer) {
            return new TaiwanNextHandPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(), buffer.readVarLong());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, TaiwanNextHandPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeUUID(value.incarnation());
            buffer.writeVarLong(value.decision());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
