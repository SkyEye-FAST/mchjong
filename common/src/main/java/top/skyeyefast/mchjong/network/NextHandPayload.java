package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Completed-hand acknowledgement for MCR, Sichuan mahjong or Taiwanese mahjong, bound to the session and decision. */
public record NextHandPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision) implements CustomPacketPayload {
    public static final Type<NextHandPayload> TYPE = new Type<>(MahjongContent.id("next_hand"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NextHandPayload> CODEC = new StreamCodec<>() {
        @Override public NextHandPayload decode(RegistryFriendlyByteBuf buffer) {
            return new NextHandPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(), buffer.readVarLong());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, NextHandPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeUUID(value.incarnation());
            buffer.writeVarLong(value.decision());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
