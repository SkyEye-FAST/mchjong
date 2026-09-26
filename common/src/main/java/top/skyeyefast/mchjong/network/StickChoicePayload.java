package top.skyeyefast.mchjong.network;

import top.skyeyefast.mchjong.platform.ResourceIds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import top.skyeyefast.mchjong.config.ServerPresets;
import top.skyeyefast.mchjong.world.MahjongContent;

/** A player's selected stick; the server shares only IDs from its own presets. */
public record StickChoicePayload(ResourceLocation preset) implements MahjongPayload {
    public static final net.minecraft.resources.ResourceLocation TYPE = MahjongContent.id("stick_choice");
    public static StickChoicePayload decode(FriendlyByteBuf buffer) {
            return new StickChoicePayload(ResourceIds.of(buffer.readUtf(128)));
        }
    @Override public void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(preset().toString(), 128);
        }
    public void handle(ServerPlayer player) { ServerPresets.chooseStick(player, preset); }
    @Override public net.minecraft.resources.ResourceLocation id() { return TYPE; }
}
