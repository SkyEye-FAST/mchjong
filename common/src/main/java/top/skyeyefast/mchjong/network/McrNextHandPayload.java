package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.world.MahjongContent;

/** MCR completed-hand acknowledgement, bound to the current session and decision. */
public record McrNextHandPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision)
    implements MahjongPayload {
    public static final ResourceLocation TYPE = MahjongContent.id("mcr_next_hand");
    public static McrNextHandPayload decode(FriendlyByteBuf buffer) {
        return new McrNextHandPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(),
            buffer.readVarLong());
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos()); buffer.writeUUID(this.tableId()); buffer.writeUUID(this.incarnation());
        buffer.writeVarLong(this.decision());
    }
    @Override public ResourceLocation id() { return TYPE; }
}
