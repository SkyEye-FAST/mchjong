package top.skyeyefast.mchjong.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import top.skyeyefast.mchjong.item.MahjongBoxMenu;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Server-authorized box operation that stores a decorative back ID on its tiles. */
public record BoxBackPayload(int containerId, ResourceLocation preset) implements MahjongPayload {
    public static final net.minecraft.resources.ResourceLocation TYPE = MahjongContent.id("box_back");
    public static BoxBackPayload decode(FriendlyByteBuf buffer) {
            return new BoxBackPayload(buffer.readVarInt(), new ResourceLocation(buffer.readUtf(128)));
        }
    @Override public void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(containerId());
            buffer.writeUtf(preset().toString(), 128);
        }
    public void handle(ServerPlayer player) {
        if (player.containerMenu instanceof MahjongBoxMenu menu && menu.containerId == containerId)
            menu.chooseBack(player, preset);
    }
    @Override public net.minecraft.resources.ResourceLocation id() { return TYPE; }
}
