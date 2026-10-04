package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.engine.MahjongVariant;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Lobby-only mahjong variant choice, separate from Riichi preset proposals. */
public record TableVariantPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision, MahjongVariant variant) implements CustomPacketPayload {
    public TableVariantPayload {
        java.util.Objects.requireNonNull(pos); java.util.Objects.requireNonNull(tableId);
        java.util.Objects.requireNonNull(incarnation); java.util.Objects.requireNonNull(variant);
        if (decision < 1) throw new IllegalArgumentException("Invalid room decision");
    }
    public static final Type<TableVariantPayload> TYPE = new Type<>(MahjongContent.id("table_variant"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TableVariantPayload> CODEC = new StreamCodec<>() {
        @Override public TableVariantPayload decode(RegistryFriendlyByteBuf buffer) {
            return new TableVariantPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(), buffer.readVarLong(), buffer.readEnum(MahjongVariant.class));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, TableVariantPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeUUID(value.incarnation());
            buffer.writeVarLong(value.decision()); buffer.writeEnum(value.variant());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
