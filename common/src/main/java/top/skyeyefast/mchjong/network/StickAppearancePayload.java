package top.skyeyefast.mchjong.network;

import top.skyeyefast.mchjong.platform.ResourceIds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.world.MahjongContent;

/** The public stick preset used for one player's deposits. */
public record StickAppearancePayload(String playerName, ResourceLocation preset) implements MahjongPayload {
    public static final net.minecraft.resources.ResourceLocation TYPE = MahjongContent.id("stick_appearance");
    public static StickAppearancePayload decode(FriendlyByteBuf buffer) {
            return new StickAppearancePayload(buffer.readUtf(64), ResourceIds.of(buffer.readUtf(128)));
        }
    @Override public void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(playerName(), 64);
            buffer.writeUtf(preset().toString(), 128);
        }
    @Override public net.minecraft.resources.ResourceLocation id() { return TYPE; }
}
