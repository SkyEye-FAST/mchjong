package top.skyeyefast.mchjong.network;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.engine.RiichiRules;
import top.skyeyefast.mchjong.engine.RiichiRuleOption;
import top.skyeyefast.mchjong.engine.RiichiPreset;
import top.skyeyefast.mchjong.world.MahjongContent;

/** A rules proposal references server-owned awards by ID, never by client-supplied definitions. */
public record RiichiRulesPayload(BlockPos pos, UUID tableId, long decision, RiichiPreset preset,
                                Map<RiichiRuleOption, Integer> settings, String yakuSetId, String yakuSetDigest) implements CustomPacketPayload {
    public RiichiRulesPayload {
        settings = new RiichiRules(preset, settings, top.skyeyefast.mchjong.engine.YakuSet.empty(top.skyeyefast.mchjong.engine.MahjongVariant.RIICHI)).settings();
        top.skyeyefast.mchjong.engine.YakuSet.requireId(yakuSetId);
        if (yakuSetDigest == null || !yakuSetDigest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid award digest");
    }
    public RiichiRulesPayload(BlockPos pos, UUID tableId, long decision, RiichiRules rules) {
        this(pos, tableId, decision, rules.preset(), rules.settings(), rules.yakuSet().id(), top.skyeyefast.mchjong.engine.YakuSetCodec.digest(rules.yakuSet()));
    }
    public RiichiRules rules(top.skyeyefast.mchjong.engine.YakuSet set) { return new RiichiRules(preset, settings, set); }
    public static final Type<RiichiRulesPayload> TYPE = new Type<>(MahjongContent.id("riichi_rules"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RiichiRulesPayload> CODEC = new StreamCodec<>() {
        @Override public RiichiRulesPayload decode(RegistryFriendlyByteBuf buffer) {
            var pos = buffer.readBlockPos();
            var id = buffer.readUUID();
            long decision = buffer.readVarLong();
            var preset = buffer.readEnum(RiichiPreset.class);
            var values = new EnumMap<RiichiRuleOption, Integer>(RiichiRuleOption.class);
            for (var option : RiichiRuleOption.values()) values.put(option, buffer.readVarInt());
            return new RiichiRulesPayload(pos, id, decision, preset, values, buffer.readUtf(128), buffer.readUtf(64));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, RiichiRulesPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeVarLong(value.decision());
            buffer.writeEnum(value.preset());
            for (var option : RiichiRuleOption.values()) buffer.writeVarInt(value.settings().get(option));
            buffer.writeUtf(value.yakuSetId(), 128);
            buffer.writeUtf(value.yakuSetDigest(), 64);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
