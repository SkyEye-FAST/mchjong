package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.world.MahjongContent;

/** A physical tile move within the sender's private hand; the server validates ownership and the decision. */
public record TableHandOrderPayload(BlockPos pos, UUID tableId, long decision, int source, int target, boolean after)
        implements MahjongPayload {
    public static final ResourceLocation TYPE = MahjongContent.id("table_hand_order");

    public static TableHandOrderPayload decode(FriendlyByteBuf buffer) {
        return new TableHandOrderPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readVarLong(),
            buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
    }

    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos()); buffer.writeUUID(tableId()); buffer.writeVarLong(decision());
        buffer.writeVarInt(source()); buffer.writeVarInt(target()); buffer.writeBoolean(after());
    }

    @Override public ResourceLocation id() { return TYPE; }
}
