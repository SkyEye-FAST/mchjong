package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Riichi preferences and preparation controls never occupy legal tile actions. */
public record RiichiControlPayload(BlockPos pos, UUID tableId, Operation operation, long token, boolean enabled)
        implements CustomPacketPayload {
    public enum Operation { AUTO_SORT, AUTO_WIN, NO_CALLS, AUTO_DISCARD, AUTO_KITA, OPEN_HANDS }
    public static final Type<RiichiControlPayload> TYPE = new Type<>(MahjongContent.id("riichi_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RiichiControlPayload> CODEC = new StreamCodec<>() {
        @Override public RiichiControlPayload decode(RegistryFriendlyByteBuf buffer) {
            return new RiichiControlPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readEnum(Operation.class),
                buffer.readVarLong(), buffer.readBoolean());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, RiichiControlPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeEnum(value.operation());
            buffer.writeVarLong(value.token()); buffer.writeBoolean(value.enabled());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
