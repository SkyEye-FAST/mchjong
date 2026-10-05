package top.skyeyefast.mchjong.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import top.skyeyefast.mchjong.item.TaiwanDeck;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.engine.TableRoomView;
import top.skyeyefast.mchjong.engine.TaiwanRoomSettings;
import top.skyeyefast.mchjong.engine.TimeControl;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Public room settings, an optional recipient-safe match view and physical appearance. */
public record TaiwanViewPayload(BlockPos pos, String view, TableRoomView room, TaiwanDeck deck, DyeColor cloth,
                             boolean open, boolean controlReply, boolean leaveDecision, TaiwanRoomSettings settings, top.skyeyefast.mchjong.world.WorldSettings.Policy world)
    implements MahjongPayload {
    public TaiwanViewPayload {
        java.util.Objects.requireNonNull(pos);
        java.util.Objects.requireNonNull(view);
        java.util.Objects.requireNonNull(room);
        if (room.variant() != top.skyeyefast.mchjong.engine.MahjongVariant.TAIWAN) throw new IllegalArgumentException("Not a Taiwan room");
        java.util.Objects.requireNonNull(cloth);
        java.util.Objects.requireNonNull(settings);
        java.util.Objects.requireNonNull(world);
    }
    public static final ResourceLocation TYPE = MahjongContent.id("taiwan_view");
    public static TaiwanViewPayload decode(FriendlyByteBuf buffer) {
        var pos = buffer.readBlockPos();
        var view = buffer.readUtf(65536);
        var room = TableNetworking.JSON.fromJson(buffer.readUtf(8192), TableRoomView.class);
        TaiwanDeck deck = null;
        if (buffer.readBoolean()) {
            var material = buffer.readEnum(TileMaterial.class);
            DyeColor back = buffer.readBoolean() ? buffer.readEnum(DyeColor.class) : null;
            var preset = new TileFacePreset(buffer.readResourceLocation());
            var backPreset = buffer.readResourceLocation();
            deck = new TaiwanDeck(buffer.readBoolean(), material, back, preset, backPreset);
        }
        return new TaiwanViewPayload(pos, view, room, deck,
            buffer.readEnum(DyeColor.class), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
            new TaiwanRoomSettings(TaiwanRulesPayload.readRules(buffer),
                new TimeControl(buffer.readVarInt(), buffer.readVarInt()), buffer.readBoolean()),
            TableNetworking.JSON.fromJson(buffer.readUtf(8192), top.skyeyefast.mchjong.world.WorldSettings.Policy.class));
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos()); buffer.writeUtf(this.view(), 65536);
        buffer.writeUtf(TableNetworking.JSON.toJson(this.room()), 8192);
        buffer.writeBoolean(this.deck() != null);
        if (this.deck() != null) {
            buffer.writeEnum(this.deck().material());
            buffer.writeBoolean(this.deck().back() != null);
            if (this.deck().back() != null) buffer.writeEnum(this.deck().back());
            buffer.writeResourceLocation(this.deck().preset().id());
            buffer.writeResourceLocation(this.deck().backPreset());
            buffer.writeBoolean(this.deck().flowers());
        }
        buffer.writeEnum(this.cloth()); buffer.writeBoolean(this.open()); buffer.writeBoolean(this.controlReply()); buffer.writeBoolean(this.leaveDecision());
        TaiwanRulesPayload.writeRules(buffer, this.settings().rules());
        buffer.writeVarInt(this.settings().timeControl().reserveSeconds()); buffer.writeVarInt(this.settings().timeControl().moveSeconds());
        buffer.writeBoolean(this.settings().rulesEditable());
        buffer.writeUtf(TableNetworking.JSON.toJson(this.world()), 8192);
    }
    @Override public ResourceLocation id() { return TYPE; }
}
