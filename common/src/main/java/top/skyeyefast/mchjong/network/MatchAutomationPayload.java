package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.engine.MatchAutomation;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Shared private preferences, separately authorized from issued game actions. */
public record MatchAutomationPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision,
                                     MatchAutomation.Option option, boolean enabled) implements CustomPacketPayload {
    public static final Type<MatchAutomationPayload> TYPE = new Type<>(MahjongContent.id("match_automation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MatchAutomationPayload> CODEC = new StreamCodec<>() {
        @Override public MatchAutomationPayload decode(RegistryFriendlyByteBuf buffer) {
            return new MatchAutomationPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(),
                buffer.readVarLong(), buffer.readEnum(MatchAutomation.Option.class), buffer.readBoolean());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, MatchAutomationPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeUUID(value.incarnation());
            buffer.writeVarLong(value.decision()); buffer.writeEnum(value.option()); buffer.writeBoolean(value.enabled());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
