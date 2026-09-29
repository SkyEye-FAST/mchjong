package top.skyeyefast.mchjong.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.DyeColor;
import top.skyeyefast.mchjong.item.McrDeck;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Only an encoded recipient-safe session view and the public physical appearance. */
public record McrViewPayload(BlockPos pos, String view, McrDeck deck, DyeColor cloth, boolean open)
    implements CustomPacketPayload {
    public McrViewPayload {
        java.util.Objects.requireNonNull(view);
        java.util.Objects.requireNonNull(deck);
        java.util.Objects.requireNonNull(cloth);
    }
    public static final Type<McrViewPayload> TYPE = new Type<>(MahjongContent.id("mcr_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, McrViewPayload> CODEC = new StreamCodec<>() {
        @Override public McrViewPayload decode(RegistryFriendlyByteBuf buffer) {
            var pos = buffer.readBlockPos();
            var view = buffer.readUtf(65536);
            var material = buffer.readEnum(TileMaterial.class);
            DyeColor back = buffer.readBoolean() ? buffer.readEnum(DyeColor.class) : null;
            var preset = new TileFacePreset(buffer.readResourceLocation());
            var backPreset = buffer.readResourceLocation();
            return new McrViewPayload(pos, view, new McrDeck(material, back, preset, backPreset),
                buffer.readEnum(DyeColor.class), buffer.readBoolean());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, McrViewPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUtf(value.view(), 65536);
            buffer.writeEnum(value.deck().material());
            buffer.writeBoolean(value.deck().back() != null);
            if (value.deck().back() != null) buffer.writeEnum(value.deck().back());
            buffer.writeResourceLocation(value.deck().preset().id());
            buffer.writeResourceLocation(value.deck().backPreset());
            buffer.writeEnum(value.cloth()); buffer.writeBoolean(value.open());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
