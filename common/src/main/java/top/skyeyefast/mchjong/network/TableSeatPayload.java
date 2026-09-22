package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Requests the server-assigned seat. The target seat is never supplied by the client. */
public record TableSeatPayload(BlockPos pos, UUID tableId) implements MahjongPayload {
    public static final ResourceLocation TYPE = MahjongContent.id("table_seat");
    public static TableSeatPayload decode(FriendlyByteBuf buffer) {
        return new TableSeatPayload(buffer.readBlockPos(), buffer.readUUID());
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos());
        buffer.writeUUID(tableId());
    }
    @Override public ResourceLocation id() { return TYPE; }
}
