package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.engine.MahjongVariant;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Lobby-only mahjong variant choice, separate from Riichi preset proposals. */
public record TableVariantPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision, MahjongVariant variant) implements MahjongPayload {
    public TableVariantPayload {
        java.util.Objects.requireNonNull(pos); java.util.Objects.requireNonNull(tableId);
        java.util.Objects.requireNonNull(incarnation); java.util.Objects.requireNonNull(variant);
        if (decision < 1) throw new IllegalArgumentException("Invalid room decision");
    }
    public static final ResourceLocation TYPE = MahjongContent.id("table_variant");
    public static TableVariantPayload decode(FriendlyByteBuf buffer) {
        return new TableVariantPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(), buffer.readVarLong(), buffer.readEnum(MahjongVariant.class));
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos()); buffer.writeUUID(this.tableId()); buffer.writeUUID(this.incarnation());
        buffer.writeVarLong(this.decision()); buffer.writeEnum(this.variant());
    }
    @Override public ResourceLocation id() { return TYPE; }
}
