package top.skyeyefast.mchjong.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Contains only an already-redacted TableView, never the persistent Game object. */
public record TableViewPayload(BlockPos pos, String view, boolean open, boolean controlReply, boolean leaveDecision, int redOptions,
                               top.skyeyefast.mchjong.engine.RoomView room) implements MahjongPayload {
    public TableViewPayload {
        if ((redOptions & ~63) != 0) throw new IllegalArgumentException("Invalid red-five capabilities");
    }
    public static final ResourceLocation TYPE = MahjongContent.id("table_view");
    public static TableViewPayload decode(FriendlyByteBuf buffer) {
        return new TableViewPayload(buffer.readBlockPos(), buffer.readUtf(32767), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readUnsignedByte(),
            TableNetworking.JSON.fromJson(buffer.readUtf(4096), top.skyeyefast.mchjong.engine.RoomView.class));
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos()); buffer.writeUtf(view(), 32767); buffer.writeBoolean(open());
        buffer.writeBoolean(controlReply());
        buffer.writeBoolean(leaveDecision());
        buffer.writeByte(redOptions());
        buffer.writeUtf(TableNetworking.JSON.toJson(room()), 4096);
    }
    @Override public ResourceLocation id() { return TYPE; }
}
