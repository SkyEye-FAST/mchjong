package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Shared room exit, vote and last-player leave decisions. */
public record TableSessionControlPayload(BlockPos pos, UUID tableId, Operation operation, long token, boolean enabled)
        implements MahjongPayload {
    public enum Operation { REQUEST_EXIT, ANSWER_EXIT, RESOLVE_LEAVE, CONVENIENCE_HINTS }
    public static final ResourceLocation TYPE = MahjongContent.id("table_session_control");
    public static TableSessionControlPayload decode(FriendlyByteBuf buffer) {
        return new TableSessionControlPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readEnum(Operation.class),
            buffer.readVarLong(), buffer.readBoolean());
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos()); buffer.writeUUID(this.tableId()); buffer.writeEnum(this.operation());
        buffer.writeVarLong(this.token()); buffer.writeBoolean(this.enabled());
    }
    @Override public ResourceLocation id() { return TYPE; }
}
