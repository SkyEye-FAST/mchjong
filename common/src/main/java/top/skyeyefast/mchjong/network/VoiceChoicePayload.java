package top.skyeyefast.mchjong.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import top.skyeyefast.mchjong.config.ServerPresets;
import top.skyeyefast.mchjong.platform.ResourceIds;
import top.skyeyefast.mchjong.world.MahjongContent;

/** The server authorizes a player's voice against its distributed archives. */
public record VoiceChoicePayload(ResourceLocation preset) implements MahjongPayload {
    public static final ResourceLocation TYPE = MahjongContent.id("voice_choice");
    public static VoiceChoicePayload decode(FriendlyByteBuf buffer) {
        return new VoiceChoicePayload(ResourceIds.of(buffer.readUtf(128)));
    }
    @Override public void write(FriendlyByteBuf buffer) { buffer.writeUtf(preset.toString(), 128); }
    public void handle(ServerPlayer player) { ServerPresets.chooseVoice(player, preset); }
    @Override public ResourceLocation id() { return TYPE; }
}
