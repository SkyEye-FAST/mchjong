package top.skyeyefast.mchjong.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.engine.TaiwanGameState;
import top.skyeyefast.mchjong.engine.TaiwanCodec;
import top.skyeyefast.mchjong.engine.YakuSet;
import top.skyeyefast.mchjong.engine.YakuSetCodec;
import top.skyeyefast.mchjong.engine.MahjongVariant;
import top.skyeyefast.mchjong.world.MahjongContent;

public record TaiwanRulesPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision, TaiwanGameState.Rules baseRules,
                                 String yakuSetId, String yakuSetDigest)
    implements CustomPacketPayload {
    public TaiwanRulesPayload {
        pos = Objects.requireNonNull(pos).immutable();
        Objects.requireNonNull(tableId);
        Objects.requireNonNull(incarnation);
        Objects.requireNonNull(baseRules);
        if (!baseRules.yakuSet().isEmpty()) throw new IllegalArgumentException("Client-supplied award definitions");
        YakuSet.requireId(yakuSetId);
        if (yakuSetDigest == null || !yakuSetDigest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid award digest");
        if (decision < 1) throw new IllegalArgumentException("Invalid Taiwan room decision");
    }
    public TaiwanRulesPayload(BlockPos pos, UUID tableId, UUID incarnation, long decision, TaiwanGameState.Rules rules) {
        this(pos, tableId, incarnation, decision, rules.withYakuSet(YakuSet.empty(MahjongVariant.TAIWAN)),
            rules.yakuSet().id(), YakuSetCodec.digest(rules.yakuSet()));
    }
    public TaiwanGameState.Rules rules(YakuSet set) { return baseRules.withYakuSet(set); }
    public static final Type<TaiwanRulesPayload> TYPE = new Type<>(MahjongContent.id("taiwan_rules"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TaiwanRulesPayload> CODEC = new StreamCodec<>() {
        @Override public TaiwanRulesPayload decode(RegistryFriendlyByteBuf buffer) {
            return new TaiwanRulesPayload(buffer.readBlockPos(), buffer.readUUID(), buffer.readUUID(), buffer.readVarLong(), readRules(buffer), buffer.readUtf(128), buffer.readUtf(64));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, TaiwanRulesPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeUUID(value.incarnation());
            buffer.writeVarLong(value.decision()); writeRules(buffer, value.baseRules());
            buffer.writeUtf(value.yakuSetId(), 128); buffer.writeUtf(value.yakuSetDigest(), 64);
        }
    };
    static TaiwanGameState.Rules readRules(RegistryFriendlyByteBuf buffer) {
        return TaiwanCodec.decodeRules(buffer.readUtf(TaiwanCodec.MAX_RULES_CHARS));
    }
    static void writeRules(RegistryFriendlyByteBuf buffer, TaiwanGameState.Rules rules) {
        buffer.writeUtf(TaiwanCodec.encodeRules(rules), TaiwanCodec.MAX_RULES_CHARS);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
