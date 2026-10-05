package top.skyeyefast.mchjong.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.engine.TaiwanGameState;
import top.skyeyefast.mchjong.engine.TaiwanCodec;
import top.skyeyefast.mchjong.world.MahjongContent;

public record TaiwanRulesPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision, TaiwanGameState.Rules rules)
    implements CustomPacketPayload {
    public TaiwanRulesPayload {
        pos = Objects.requireNonNull(pos).immutable();
        Objects.requireNonNull(tableId);
        Objects.requireNonNull(incarnation);
        Objects.requireNonNull(rules);
        if (decision < 1) throw new IllegalArgumentException("Invalid Taiwan room decision");
    }
    public static final Type<TaiwanRulesPayload> TYPE = new Type<>(MahjongContent.id("taiwan_rules"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TaiwanRulesPayload> CODEC = new StreamCodec<>() {
        @Override public TaiwanRulesPayload decode(RegistryFriendlyByteBuf buffer) {
            return new TaiwanRulesPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(), buffer.readVarLong(), readRules(buffer));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, TaiwanRulesPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeUUID(value.incarnation());
            buffer.writeVarLong(value.decision()); writeRules(buffer, value.rules());
        }
    };
    static TaiwanGameState.Rules readRules(RegistryFriendlyByteBuf buffer) {
        return TaiwanCodec.decodeRules(buffer.readUtf(65536));
    }
    static void writeRules(RegistryFriendlyByteBuf buffer, TaiwanGameState.Rules rules) {
        buffer.writeUtf(TaiwanCodec.encodeRules(rules), 65536);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
