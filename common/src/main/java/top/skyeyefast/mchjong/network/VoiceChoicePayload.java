package top.skyeyefast.mchjong.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import top.skyeyefast.mchjong.config.ServerPresets;
import top.skyeyefast.mchjong.world.MahjongContent;

/** The server authorizes a player's voice against its distributed archives. */
public record VoiceChoicePayload(ResourceLocation preset) implements CustomPacketPayload {
    public static final Type<VoiceChoicePayload> TYPE = new Type<>(MahjongContent.id("voice_choice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VoiceChoicePayload> CODEC = new StreamCodec<>() {
        @Override public VoiceChoicePayload decode(RegistryFriendlyByteBuf buffer) {
            return new VoiceChoicePayload(ResourceLocation.parse(buffer.readUtf(128)));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, VoiceChoicePayload value) {
            buffer.writeUtf(value.preset().toString(), 128);
        }
    };
    public void handle(ServerPlayer player) { ServerPresets.chooseVoice(player, preset); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
