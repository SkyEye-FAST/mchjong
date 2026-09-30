package top.skyeyefast.mchjong.network;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.engine.MahjongVariant;
import top.skyeyefast.mchjong.engine.TableRoomView;
import top.skyeyefast.mchjong.engine.TimeControl;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Recipient-safe Sichuan session and shared room projection. */
public record SichuanViewPayload(BlockPos pos, String view, TableRoomView room, boolean open,
                                 boolean leaveDecision, TimeControl timeControl) implements CustomPacketPayload {
    public SichuanViewPayload {
        Objects.requireNonNull(pos); Objects.requireNonNull(view); Objects.requireNonNull(room); Objects.requireNonNull(timeControl);
        if (room.variant() != MahjongVariant.SICHUAN) throw new IllegalArgumentException("Not a Sichuan room");
    }
    public static final Type<SichuanViewPayload> TYPE = new Type<>(MahjongContent.id("sichuan_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SichuanViewPayload> CODEC = new StreamCodec<>() {
        @Override public SichuanViewPayload decode(RegistryFriendlyByteBuf buffer) {
            return new SichuanViewPayload(buffer.readBlockPos(), buffer.readUtf(65536),
                TableNetworking.JSON.fromJson(buffer.readUtf(8192), TableRoomView.class), buffer.readBoolean(), buffer.readBoolean(),
                new TimeControl(buffer.readVarInt(), buffer.readVarInt()));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, SichuanViewPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUtf(value.view(), 65536);
            buffer.writeUtf(TableNetworking.JSON.toJson(value.room()), 8192);
            buffer.writeBoolean(value.open()); buffer.writeBoolean(value.leaveDecision());
            buffer.writeVarInt(value.timeControl().reserveSeconds()); buffer.writeVarInt(value.timeControl().moveSeconds());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
