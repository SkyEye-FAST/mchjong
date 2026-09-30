package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.world.MahjongContent;

public record SichuanNextHandPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision)
    implements CustomPacketPayload {
    public static final Type<SichuanNextHandPayload> TYPE = new Type<>(MahjongContent.id("sichuan_next_hand"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SichuanNextHandPayload> CODEC = new StreamCodec<>() {
        @Override public SichuanNextHandPayload decode(RegistryFriendlyByteBuf buffer) {
            return new SichuanNextHandPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(), buffer.readVarLong());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, SichuanNextHandPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeUUID(value.incarnation());
            buffer.writeVarLong(value.decision());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
