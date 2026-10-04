package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.world.MahjongContent;

/** An issued Taiwan action; the authenticated sender determines the seat. */
public record TaiwanActionPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision, int actionIndex)
    implements CustomPacketPayload {
    public TaiwanActionPayload {
        if (actionIndex < 0) throw new IllegalArgumentException("Invalid Taiwan action index");
    }
    public static final Type<TaiwanActionPayload> TYPE = new Type<>(MahjongContent.id("taiwan_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TaiwanActionPayload> CODEC = new StreamCodec<>() {
        @Override public TaiwanActionPayload decode(RegistryFriendlyByteBuf buffer) {
            return new TaiwanActionPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(), buffer.readVarLong(), buffer.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, TaiwanActionPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeUUID(value.incarnation());
            buffer.writeVarLong(value.decision()); buffer.writeVarInt(value.actionIndex());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
