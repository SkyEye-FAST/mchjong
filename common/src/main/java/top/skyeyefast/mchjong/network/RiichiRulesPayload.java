package top.skyeyefast.mchjong.network;

import java.util.EnumMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.skyeyefast.mchjong.engine.RiichiRules;
import top.skyeyefast.mchjong.engine.RiichiRuleOption;
import top.skyeyefast.mchjong.engine.RiichiPreset;
import top.skyeyefast.mchjong.world.MahjongContent;

/** A bounded, complete proposal, authorized by the table identity and current lobby decision. */
public record RiichiRulesPayload(BlockPos pos, UUID tableId, long decision, RiichiRules rules) implements CustomPacketPayload {
    public static final Type<RiichiRulesPayload> TYPE = new Type<>(MahjongContent.id("riichi_rules"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RiichiRulesPayload> CODEC = new StreamCodec<>() {
        @Override public RiichiRulesPayload decode(RegistryFriendlyByteBuf buffer) {
            var pos = buffer.readBlockPos();
            var id = buffer.readUUID();
            long decision = buffer.readVarLong();
            var preset = buffer.readEnum(RiichiPreset.class);
            var values = new EnumMap<RiichiRuleOption, Integer>(RiichiRuleOption.class);
            for (var option : RiichiRuleOption.values()) values.put(option, buffer.readVarInt());
            return new RiichiRulesPayload(pos, id, decision, new RiichiRules(preset, values));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, RiichiRulesPayload value) {
            buffer.writeBlockPos(value.pos()); buffer.writeUUID(value.tableId()); buffer.writeVarLong(value.decision());
            buffer.writeEnum(value.rules().preset());
            for (var option : RiichiRuleOption.values()) buffer.writeVarInt(value.rules().get(option));
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
