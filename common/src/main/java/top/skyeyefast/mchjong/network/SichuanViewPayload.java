package top.skyeyefast.mchjong.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.DyeColor;
import top.skyeyefast.mchjong.item.SichuanDeck;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.engine.TableRoomView;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Only an encoded recipient-safe session view and the public physical appearance. */
public record SichuanViewPayload(BlockPos pos, String view, TableRoomView room, SichuanDeck deck, DyeColor cloth,
                             boolean open, boolean leaveDecision, top.skyeyefast.mchjong.engine.TimeControl timeControl)
    implements CustomPacketPayload {
    public SichuanViewPayload {
        java.util.Objects.requireNonNull(pos);
        java.util.Objects.requireNonNull(view);
        java.util.Objects.requireNonNull(room);
        if (room.variant() != top.skyeyefast.mchjong.engine.MahjongVariant.SICHUAN) throw new IllegalArgumentException("Not a Sichuan room");
        java.util.Objects.requireNonNull(cloth);
        java.util.Objects.requireNonNull(timeControl);
    }
    public static final Type<SichuanViewPayload> TYPE = new Type<>(MahjongContent.id("sichuan_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SichuanViewPayload> CODEC = new StreamCodec<>() {
        @Override public SichuanViewPayload decode(RegistryFriendlyByteBuf buffer) {
            var pos = buffer.readBlockPos();
            var view = buffer.readUtf(65536);
            var room = TableNetworking.JSON.fromJson(buffer.readUtf(8192), TableRoomView.class);
            SichuanDeck deck = null;
            if (buffer.readBoolean()) {
                var material = buffer.readEnum(TileMaterial.class);
                DyeColor back = buffer.readBoolean() ? buffer.readEnum(DyeColor.class) : null;
                var preset = new TileFacePreset(buffer.readResourceLocation());
                var backPreset = buffer.readResourceLocation();
                deck = new SichuanDeck(material, back, preset, backPreset);
            }
            return new SichuanViewPayload(pos, view, room, deck,
                buffer.readEnum(DyeColor.class), buffer.readBoolean(), buffer.readBoolean(),
                new top.skyeyefast.mchjong.engine.TimeControl(buffer.readVarInt(), buffer.readVarInt()));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, SichuanViewPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUtf(value.view(), 65536);
            buffer.writeUtf(TableNetworking.JSON.toJson(value.room()), 8192);
            buffer.writeBoolean(value.deck() != null);
            if (value.deck() != null) {
                buffer.writeEnum(value.deck().material());
                buffer.writeBoolean(value.deck().back() != null);
                if (value.deck().back() != null) buffer.writeEnum(value.deck().back());
                buffer.writeResourceLocation(value.deck().preset().id());
                buffer.writeResourceLocation(value.deck().backPreset());
            }
            buffer.writeEnum(value.cloth()); buffer.writeBoolean(value.open()); buffer.writeBoolean(value.leaveDecision());
            buffer.writeVarInt(value.timeControl().reserveSeconds()); buffer.writeVarInt(value.timeControl().moveSeconds());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
