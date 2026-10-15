package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Completed-hand acknowledgement for MCR, Sichuan mahjong or Taiwanese mahjong, bound to the session and decision. */
public record NextHandPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision) implements MahjongPayload {
    public static final ResourceLocation TYPE = MahjongContent.id("next_hand");
    public static NextHandPayload decode(FriendlyByteBuf buffer) {
        return new NextHandPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(), buffer.readVarLong());
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos); buffer.writeUUID(tableId); buffer.writeUUID(incarnation);
        buffer.writeVarLong(decision);
    }
    @Override public ResourceLocation id() { return TYPE; }
}
