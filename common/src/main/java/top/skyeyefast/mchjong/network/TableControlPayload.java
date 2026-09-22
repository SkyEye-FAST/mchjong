package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Administrative table controls never occupy or renumber legal tile actions. */
public record TableControlPayload(BlockPos pos, UUID tableId, Operation operation, long token, boolean enabled)
        implements MahjongPayload {
    public enum Operation { REQUEST_EXIT, ANSWER_EXIT, AUTO_SORT, AUTO_WIN, NO_CALLS, AUTO_DISCARD, AUTO_KITA }
    public static final ResourceLocation TYPE = MahjongContent.id("table_control");
    public static TableControlPayload decode(FriendlyByteBuf buffer) {
        return new TableControlPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readEnum(Operation.class),
            buffer.readVarLong(), buffer.readBoolean());
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos()); buffer.writeUUID(tableId()); buffer.writeEnum(operation());
        buffer.writeVarLong(token()); buffer.writeBoolean(enabled());
    }
    @Override public ResourceLocation id() { return TYPE; }
}
