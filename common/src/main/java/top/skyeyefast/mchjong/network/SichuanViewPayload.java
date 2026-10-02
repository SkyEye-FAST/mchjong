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
import top.skyeyefast.mchjong.engine.SichuanRoomSettings;
import top.skyeyefast.mchjong.engine.TimeControl;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Public room settings, an optional recipient-safe match view and physical appearance. */
public record SichuanViewPayload(BlockPos pos, String view, TableRoomView room, SichuanDeck deck, DyeColor cloth,
                             boolean open, boolean controlReply, boolean leaveDecision, SichuanRoomSettings settings, top.skyeyefast.mchjong.world.WorldSettings.Policy world)
    implements CustomPacketPayload {
    public SichuanViewPayload {
        java.util.Objects.requireNonNull(pos);
        java.util.Objects.requireNonNull(view);
        java.util.Objects.requireNonNull(room);
        if (room.variant() != top.skyeyefast.mchjong.engine.MahjongVariant.SICHUAN) throw new IllegalArgumentException("Not a Sichuan room");
        java.util.Objects.requireNonNull(cloth);
        java.util.Objects.requireNonNull(settings);
        java.util.Objects.requireNonNull(world);
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
                var preset = new TileFacePreset(buffer.readIdentifier());
                var backPreset = buffer.readIdentifier();
                deck = new SichuanDeck(material, back, preset, backPreset);
            }
            return new SichuanViewPayload(pos, view, room, deck,
                buffer.readEnum(DyeColor.class), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                new SichuanRoomSettings(SichuanRulesPayload.readRules(buffer),
                    new TimeControl(buffer.readVarInt(), buffer.readVarInt()), buffer.readBoolean()),
                TableNetworking.JSON.fromJson(buffer.readUtf(8192), top.skyeyefast.mchjong.world.WorldSettings.Policy.class));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, SichuanViewPayload value) {
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
            SichuanRulesPayload.writeRules(buffer, value.settings().rules());
            buffer.writeVarInt(value.settings().timeControl().reserveSeconds()); buffer.writeVarInt(value.settings().timeControl().moveSeconds());
            buffer.writeBoolean(value.settings().rulesEditable());
            buffer.writeUtf(TableNetworking.JSON.toJson(value.world()), 8192);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
