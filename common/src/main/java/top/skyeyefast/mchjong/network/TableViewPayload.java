package top.skyeyefast.mchjong.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.engine.MahjongVariant;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Contains only an already-redacted TableView, never the persistent RiichiGame object. */
public record TableViewPayload(BlockPos pos, String view, boolean open, boolean controlReply, boolean leaveDecision, int redOptions,
                               top.skyeyefast.mchjong.engine.TableRoomView room,
                               top.skyeyefast.mchjong.world.BotServiceState botService,
                               top.skyeyefast.mchjong.world.WorldSettings.Policy world, MahjongVariant variant) implements CustomPacketPayload {
    public TableViewPayload {
        if ((redOptions & ~63) != 0) throw new IllegalArgumentException("Invalid red-five capabilities");
    }
    public static final Type<TableViewPayload> TYPE = new Type<>(MahjongContent.id("table_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TableViewPayload> CODEC = new StreamCodec<>() {
        @Override public TableViewPayload decode(RegistryFriendlyByteBuf buffer) {
            return new TableViewPayload(buffer.readBlockPos(), buffer.readUtf(32767), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readUnsignedByte(),
                TableNetworking.JSON.fromJson(buffer.readUtf(8192), top.skyeyefast.mchjong.engine.TableRoomView.class),
                TableNetworking.JSON.fromJson(buffer.readUtf(2048), top.skyeyefast.mchjong.world.BotServiceState.class),
                TableNetworking.JSON.fromJson(buffer.readUtf(8192), top.skyeyefast.mchjong.world.WorldSettings.Policy.class),
                buffer.readEnum(MahjongVariant.class));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, TableViewPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUtf(value.view(), 32767); buffer.writeBoolean(value.open());
            buffer.writeBoolean(value.controlReply());
            buffer.writeBoolean(value.leaveDecision());
            buffer.writeByte(value.redOptions());
            buffer.writeUtf(TableNetworking.JSON.toJson(value.room()), 8192);
            buffer.writeUtf(TableNetworking.JSON.toJson(value.botService()), 2048);
            buffer.writeUtf(TableNetworking.JSON.toJson(value.world()), 8192);
            buffer.writeEnum(value.variant());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
