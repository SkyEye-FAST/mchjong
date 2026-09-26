package top.skyeyefast.mchjong.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import top.skyeyefast.mchjong.item.MahjongBoxMenu;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Server-authorized box operation that stores a decorative back ID on its tiles. */
public record BoxBackPayload(int containerId, Identifier preset) implements CustomPacketPayload {
    public static final Type<BoxBackPayload> TYPE = new Type<>(MahjongContent.id("box_back"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BoxBackPayload> CODEC = new StreamCodec<>() {
        @Override public BoxBackPayload decode(RegistryFriendlyByteBuf buffer) {
            return new BoxBackPayload(buffer.readVarInt(), Identifier.parse(buffer.readUtf(128)));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, BoxBackPayload value) {
            buffer.writeVarInt(value.containerId());
            buffer.writeUtf(value.preset().toString(), 128);
        }
    };
    public void handle(ServerPlayer player) {
        if (player.containerMenu instanceof MahjongBoxMenu menu && menu.containerId == containerId)
            menu.chooseBack(player, preset);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
