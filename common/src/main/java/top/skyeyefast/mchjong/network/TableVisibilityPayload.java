package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.engine.HandVisibility;
import top.skyeyefast.mchjong.world.MahjongContent;

/** A host proposal bound to the current room and preparation decision. */
public record TableVisibilityPayload(BlockPos pos, UUID tableId, long decision, HandVisibility visibility)
        implements CustomPacketPayload {
    public static final Type<TableVisibilityPayload> TYPE = new Type<>(MahjongContent.id("table_visibility"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TableVisibilityPayload> CODEC = new StreamCodec<>() {
        @Override public TableVisibilityPayload decode(RegistryFriendlyByteBuf buffer) {
            return new TableVisibilityPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readVarLong(),
                buffer.readEnum(HandVisibility.class));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, TableVisibilityPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeVarLong(value.decision());
            buffer.writeEnum(value.visibility());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
