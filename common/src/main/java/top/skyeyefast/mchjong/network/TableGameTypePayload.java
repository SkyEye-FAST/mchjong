package top.skyeyefast.mchjong.network;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.engine.GameType;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Lobby-only top-level game choice, separate from Riichi RuleSet proposals. */
public record TableGameTypePayload(BlockPos pos, UUID tableId, long decision, GameType gameType) implements CustomPacketPayload {
    public static final Type<TableGameTypePayload> TYPE = new Type<>(MahjongContent.id("table_game_type"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TableGameTypePayload> CODEC = new StreamCodec<>() {
        @Override public TableGameTypePayload decode(RegistryFriendlyByteBuf buffer) {
            return new TableGameTypePayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readVarLong(), buffer.readEnum(GameType.class));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, TableGameTypePayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId());
            buffer.writeVarLong(value.decision()); buffer.writeEnum(value.gameType());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
