package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Riichi preferences and preparation controls never occupy legal tile actions. */
public record RiichiControlPayload(BlockPos pos, UUID tableId, Operation operation, long token, boolean enabled)
        implements MahjongPayload {
    public enum Operation { AUTO_SORT, AUTO_KITA, OPEN_HANDS }
    public static final ResourceLocation TYPE = MahjongContent.id("riichi_control");
    public static RiichiControlPayload decode(FriendlyByteBuf buffer) {
        return new RiichiControlPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readEnum(Operation.class),
            buffer.readVarLong(), buffer.readBoolean());
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos()); buffer.writeUUID(this.tableId()); buffer.writeEnum(this.operation());
        buffer.writeVarLong(this.token()); buffer.writeBoolean(this.enabled());
    }
    @Override public ResourceLocation id() { return TYPE; }
}
