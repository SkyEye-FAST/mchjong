package top.skyeyefast.mchjong.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Contains only an already-redacted TableView, never the persistent Game object. */
public record TableViewPayload(BlockPos pos, String view, boolean open, boolean controlReply, int redOptions) implements CustomPacketPayload {
    public TableViewPayload {
        if ((redOptions & ~63) != 0) throw new IllegalArgumentException("Invalid red-five capabilities");
    }
    public static final Type<TableViewPayload> TYPE = new Type<>(MahjongContent.id("table_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TableViewPayload> CODEC = new StreamCodec<>() {
        @Override public TableViewPayload decode(RegistryFriendlyByteBuf buffer) {
            return new TableViewPayload(buffer.readBlockPos(), buffer.readUtf(32767), buffer.readBoolean(), buffer.readBoolean(), buffer.readUnsignedByte());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, TableViewPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUtf(value.view(), 32767); buffer.writeBoolean(value.open());
            buffer.writeBoolean(value.controlReply());
            buffer.writeByte(value.redOptions());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
