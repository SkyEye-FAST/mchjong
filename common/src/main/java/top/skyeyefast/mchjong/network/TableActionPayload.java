package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.world.MahjongContent;

/** No client-supplied tiles, scores, usernames or seat ownership. */
public record TableActionPayload(BlockPos pos, UUID tableId, long decision, int action) implements MahjongPayload {
    public static final ResourceLocation TYPE = MahjongContent.id("table_action");
    public static TableActionPayload decode(FriendlyByteBuf buffer) {
        return new TableActionPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readVarLong(), buffer.readVarInt());
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos()); buffer.writeUUID(tableId());
        buffer.writeVarLong(decision()); buffer.writeVarInt(action());
    }
    @Override public ResourceLocation id() { return TYPE; }
}
