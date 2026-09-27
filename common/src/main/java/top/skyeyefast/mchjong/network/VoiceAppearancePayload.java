package top.skyeyefast.mchjong.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Public recording selection, associated with the server-authenticated player name. */
public record VoiceAppearancePayload(String playerName, ResourceLocation preset) implements CustomPacketPayload {
    public static final Type<VoiceAppearancePayload> TYPE = new Type<>(MahjongContent.id("voice_appearance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VoiceAppearancePayload> CODEC = new StreamCodec<>() {
        @Override public VoiceAppearancePayload decode(RegistryFriendlyByteBuf buffer) {
            return new VoiceAppearancePayload(buffer.readUtf(64), ResourceLocation.parse(buffer.readUtf(128)));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, VoiceAppearancePayload value) {
            buffer.writeUtf(value.playerName(), 64);
            buffer.writeUtf(value.preset().toString(), 128);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
