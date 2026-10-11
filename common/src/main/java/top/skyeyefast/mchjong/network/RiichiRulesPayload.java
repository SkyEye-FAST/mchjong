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
                                Map<RiichiRuleOption, Integer> settings, YakuSelectionRequest selection) implements CustomPacketPayload {
    public RiichiRulesPayload {
        settings = new RiichiRules(preset, settings, top.skyeyefast.mchjong.engine.YakuSelection.defaults(top.skyeyefast.mchjong.engine.MahjongVariant.RIICHI)).settings();
        java.util.Objects.requireNonNull(selection);
    }
    public RiichiRulesPayload(BlockPos pos, UUID tableId, long decision, RiichiRules rules) {
        this(pos, tableId, decision, rules.preset(), rules.settings(), YakuSelectionRequest.of(rules.yakuSelection()));
    }
    public RiichiRules rules(top.skyeyefast.mchjong.engine.YakuSelection selection) {
        try { return new RiichiRules(preset, settings, selection); }
        catch (IllegalArgumentException invalid) { return null; }
    }
    public static final Type<RiichiRulesPayload> TYPE = new Type<>(MahjongContent.id("riichi_rules"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RiichiRulesPayload> CODEC = new StreamCodec<>() {
        @Override public RiichiRulesPayload decode(RegistryFriendlyByteBuf buffer) {
            var pos = buffer.readBlockPos();
            var id = buffer.readUUID();
            long decision = buffer.readVarLong();
            var preset = buffer.readEnum(RiichiPreset.class);
            var values = new EnumMap<RiichiRuleOption, Integer>(RiichiRuleOption.class);
            for (var option : RiichiRuleOption.values()) values.put(option, buffer.readVarInt());
            return new RiichiRulesPayload(pos, id, decision, preset, values, YakuSelectionRequest.read(buffer));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, RiichiRulesPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeVarLong(value.decision());
            buffer.writeEnum(value.preset());
            for (var option : RiichiRuleOption.values()) buffer.writeVarInt(value.settings().get(option));
            value.selection().write(buffer);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
