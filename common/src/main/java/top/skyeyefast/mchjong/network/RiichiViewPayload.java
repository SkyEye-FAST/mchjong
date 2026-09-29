package top.skyeyefast.mchjong.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.engine.MahjongVariant;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Carries the common room, Riichi settings and an optional redacted active-match view. */
public record RiichiViewPayload(BlockPos pos, String view, boolean open, boolean controlReply, boolean leaveDecision, int redOptions,
                               top.skyeyefast.mchjong.engine.TableRoomView room,
                               top.skyeyefast.mchjong.engine.RiichiRoomSettings settings,
                               top.skyeyefast.mchjong.world.BotServiceState botService,
                               top.skyeyefast.mchjong.world.WorldSettings.Policy world, MahjongVariant variant) implements CustomPacketPayload {
    public RiichiViewPayload {
        if ((redOptions & ~63) != 0) throw new IllegalArgumentException("Invalid red-five capabilities");
    }
    public static final Type<RiichiViewPayload> TYPE = new Type<>(MahjongContent.id("riichi_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RiichiViewPayload> CODEC = new StreamCodec<>() {
        @Override public RiichiViewPayload decode(RegistryFriendlyByteBuf buffer) {
            return new RiichiViewPayload(buffer.readBlockPos(), buffer.readUtf(32767), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readUnsignedByte(),
                TableNetworking.JSON.fromJson(buffer.readUtf(8192), top.skyeyefast.mchjong.engine.TableRoomView.class),
                TableNetworking.JSON.fromJson(buffer.readUtf(8192), top.skyeyefast.mchjong.engine.RiichiRoomSettings.class),
                TableNetworking.JSON.fromJson(buffer.readUtf(2048), top.skyeyefast.mchjong.world.BotServiceState.class),
                TableNetworking.JSON.fromJson(buffer.readUtf(8192), top.skyeyefast.mchjong.world.WorldSettings.Policy.class),
                buffer.readEnum(MahjongVariant.class));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, RiichiViewPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUtf(value.view(), 32767); buffer.writeBoolean(value.open());
            buffer.writeBoolean(value.controlReply());
            buffer.writeBoolean(value.leaveDecision());
            buffer.writeByte(value.redOptions());
            buffer.writeUtf(TableNetworking.JSON.toJson(value.room()), 8192);
            buffer.writeUtf(TableNetworking.JSON.toJson(value.settings()), 8192);
            buffer.writeUtf(TableNetworking.JSON.toJson(value.botService()), 2048);
            buffer.writeUtf(TableNetworking.JSON.toJson(value.world()), 8192);
            buffer.writeEnum(value.variant());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
