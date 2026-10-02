package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.world.MahjongContent;

/** A physical tile move within the sender's private hand; the server validates ownership and the decision. */
public record RiichiHandOrderPayload(BlockPos pos, UUID tableId, long decision, int source, int target, boolean after)
        implements MahjongPayload {
    public static final ResourceLocation TYPE = MahjongContent.id("riichi_hand_order");
    public static RiichiHandOrderPayload decode(FriendlyByteBuf buffer) {
        return new RiichiHandOrderPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readVarLong(),
            buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos()); buffer.writeUUID(this.tableId()); buffer.writeVarLong(this.decision());
        buffer.writeVarInt(this.source()); buffer.writeVarInt(this.target()); buffer.writeBoolean(this.after());
    }
    @Override public ResourceLocation id() { return TYPE; }
}
