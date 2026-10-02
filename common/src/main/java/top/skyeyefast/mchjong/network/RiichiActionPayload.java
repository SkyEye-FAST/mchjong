package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.world.MahjongContent;

/** No client-supplied tiles, scores, usernames or seat ownership. */
public record RiichiActionPayload(BlockPos pos, UUID tableId, long decision, int action) implements MahjongPayload {
    public static final ResourceLocation TYPE = MahjongContent.id("riichi_action");
    public static RiichiActionPayload decode(FriendlyByteBuf buffer) {
        return new RiichiActionPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readVarLong(), buffer.readVarInt());
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos()); buffer.writeUUID(this.tableId());
        buffer.writeVarLong(this.decision()); buffer.writeVarInt(this.action());
    }
    @Override public ResourceLocation id() { return TYPE; }
}
