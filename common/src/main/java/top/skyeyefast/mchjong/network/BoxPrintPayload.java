package top.skyeyefast.mchjong.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import top.skyeyefast.mchjong.item.MahjongBoxMenu;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Only a cosmetic ID crosses the network; stock, carrier and reagent remain server-authorized. */
public record BoxPrintPayload(int containerId, TileFacePreset preset) implements MahjongPayload {
    public static final ResourceLocation TYPE = MahjongContent.id("box_print");
    public static BoxPrintPayload decode(FriendlyByteBuf buffer) {
        return new BoxPrintPayload(buffer.readVarInt(), new TileFacePreset(new ResourceLocation(buffer.readUtf(128))));
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId());
        buffer.writeUtf(preset().getSerializedName(), 128);
    }
    public void handle(ServerPlayer player) {
        if (player.containerMenu instanceof MahjongBoxMenu menu && menu.containerId == containerId)
            menu.print(player, preset);
    }
    @Override public ResourceLocation id() { return TYPE; }
}
