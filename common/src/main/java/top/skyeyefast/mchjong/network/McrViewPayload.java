package top.skyeyefast.mchjong.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.DyeColor;
import top.skyeyefast.mchjong.item.McrDeck;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.engine.TableRoomView;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Only an encoded recipient-safe session view and the public physical appearance. */
public record McrViewPayload(BlockPos pos, String view, TableRoomView room, McrDeck deck, DyeColor cloth,
                             boolean open, boolean controlReply, boolean leaveDecision, top.skyeyefast.mchjong.engine.TimeControl timeControl, top.skyeyefast.mchjong.world.WorldSettings.Policy world)
    implements CustomPacketPayload {
    public McrViewPayload {
        java.util.Objects.requireNonNull(view);
        java.util.Objects.requireNonNull(room);
        java.util.Objects.requireNonNull(cloth);
        java.util.Objects.requireNonNull(timeControl);
        java.util.Objects.requireNonNull(world);
    }
    public static final Type<McrViewPayload> TYPE = new Type<>(MahjongContent.id("mcr_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, McrViewPayload> CODEC = new StreamCodec<>() {
        @Override public McrViewPayload decode(RegistryFriendlyByteBuf buffer) {
            var pos = buffer.readBlockPos();
            var view = buffer.readUtf(65536);
            var room = TableNetworking.JSON.fromJson(buffer.readUtf(8192), TableRoomView.class);
            McrDeck deck = null;
            if (buffer.readBoolean()) {
                var material = buffer.readEnum(TileMaterial.class);
                DyeColor back = buffer.readBoolean() ? buffer.readEnum(DyeColor.class) : null;
                var preset = new TileFacePreset(buffer.readIdentifier());
                var backPreset = buffer.readIdentifier();
                deck = new McrDeck(material, back, preset, backPreset);
            }
            return new McrViewPayload(pos, view, room, deck,
                buffer.readEnum(DyeColor.class), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                new top.skyeyefast.mchjong.engine.TimeControl(buffer.readVarInt(), buffer.readVarInt()),
                TableNetworking.JSON.fromJson(buffer.readUtf(8192), top.skyeyefast.mchjong.world.WorldSettings.Policy.class));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, McrViewPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUtf(value.view(), 65536);
            buffer.writeUtf(TableNetworking.JSON.toJson(value.room()), 8192);
            buffer.writeBoolean(value.deck() != null);
            if (value.deck() != null) {
                buffer.writeEnum(value.deck().material());
                buffer.writeBoolean(value.deck().back() != null);
                if (value.deck().back() != null) buffer.writeEnum(value.deck().back());
                buffer.writeIdentifier(value.deck().preset().id());
                buffer.writeIdentifier(value.deck().backPreset());
            }
            buffer.writeEnum(value.cloth()); buffer.writeBoolean(value.open()); buffer.writeBoolean(value.controlReply()); buffer.writeBoolean(value.leaveDecision());
            buffer.writeVarInt(value.timeControl().reserveSeconds()); buffer.writeVarInt(value.timeControl().moveSeconds());
            buffer.writeUtf(TableNetworking.JSON.toJson(value.world()), 8192);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
