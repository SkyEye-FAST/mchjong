package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.engine.PlayerHandVisibility;
import top.skyeyefast.mchjong.world.MahjongContent;

/** A host proposal bound to the current room and preparation decision. */
public record RiichiVisibilityPayload(BlockPos pos, UUID tableId, long decision, PlayerHandVisibility visibility)
        implements CustomPacketPayload {
    public static final Type<RiichiVisibilityPayload> TYPE = new Type<>(MahjongContent.id("riichi_visibility"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RiichiVisibilityPayload> CODEC = new StreamCodec<>() {
        @Override public RiichiVisibilityPayload decode(RegistryFriendlyByteBuf buffer) {
            return new RiichiVisibilityPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readVarLong(),
                buffer.readEnum(PlayerHandVisibility.class));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, RiichiVisibilityPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeVarLong(value.decision());
            buffer.writeEnum(value.visibility());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
