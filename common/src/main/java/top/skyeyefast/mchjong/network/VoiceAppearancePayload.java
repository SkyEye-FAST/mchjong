package top.skyeyefast.mchjong.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.platform.ResourceIds;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Public recording selection, associated with the server-authenticated player name. */
public record VoiceAppearancePayload(String playerName, ResourceLocation preset) implements MahjongPayload {
    public static final ResourceLocation TYPE = MahjongContent.id("voice_appearance");
    public static VoiceAppearancePayload decode(FriendlyByteBuf buffer) {
        return new VoiceAppearancePayload(buffer.readUtf(64), ResourceIds.of(buffer.readUtf(128)));
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(playerName, 64);
        buffer.writeUtf(preset.toString(), 128);
    }
    @Override public ResourceLocation id() { return TYPE; }
}
