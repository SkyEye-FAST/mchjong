package top.skyeyefast.mchjong.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import top.skyeyefast.mchjong.item.MahjongBoxMenu;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Only a cosmetic ID crosses the network; stock, carrier and reagent remain server-authorized. */
public record BoxPrintPayload(int containerId, TileFacePreset preset) implements CustomPacketPayload {
    public static final Type<BoxPrintPayload> TYPE = new Type<>(MahjongContent.id("box_print"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BoxPrintPayload> CODEC = new StreamCodec<>() {
        @Override public BoxPrintPayload decode(RegistryFriendlyByteBuf buffer) {
            return new BoxPrintPayload(buffer.readVarInt(), new TileFacePreset(ResourceLocation.parse(buffer.readUtf(128))));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, BoxPrintPayload value) {
            buffer.writeVarInt(value.containerId());
            buffer.writeUtf(value.preset().getSerializedName(), 128);
        }
    };
    public void handle(ServerPlayer player) {
        if (player.containerMenu instanceof MahjongBoxMenu menu && menu.containerId == containerId)
            menu.print(player, preset);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
