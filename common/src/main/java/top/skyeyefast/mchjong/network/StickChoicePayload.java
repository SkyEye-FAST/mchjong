package top.skyeyefast.mchjong.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import top.skyeyefast.mchjong.config.ServerPresets;
import top.skyeyefast.mchjong.world.MahjongContent;

/** A player's selected stick; the server shares only IDs from its own presets. */
public record StickChoicePayload(Identifier preset) implements CustomPacketPayload {
    public static final Type<StickChoicePayload> TYPE = new Type<>(MahjongContent.id("stick_choice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StickChoicePayload> CODEC = new StreamCodec<>() {
        @Override public StickChoicePayload decode(RegistryFriendlyByteBuf buffer) {
            return new StickChoicePayload(Identifier.parse(buffer.readUtf(128)));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, StickChoicePayload value) {
            buffer.writeUtf(value.preset().toString(), 128);
        }
    };
    public void handle(ServerPlayer player) { ServerPresets.chooseStick(player, preset); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
