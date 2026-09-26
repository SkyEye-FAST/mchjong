package top.skyeyefast.mchjong.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.skyeyefast.mchjong.world.MahjongContent;

/** The public stick preset used for one player's deposits. */
public record StickAppearancePayload(String playerName, Identifier preset) implements CustomPacketPayload {
    public static final Type<StickAppearancePayload> TYPE = new Type<>(MahjongContent.id("stick_appearance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StickAppearancePayload> CODEC = new StreamCodec<>() {
        @Override public StickAppearancePayload decode(RegistryFriendlyByteBuf buffer) {
            return new StickAppearancePayload(buffer.readUtf(64), Identifier.parse(buffer.readUtf(128)));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, StickAppearancePayload value) {
            buffer.writeUtf(value.playerName(), 64);
            buffer.writeUtf(value.preset().toString(), 128);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
